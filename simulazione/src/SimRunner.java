package src;

import java.io.File;
import java.util.List;

/**
 * Esecuzione SENZA grafica della simulazione, veloce, per provare l'evoluzione e tarare i
 * parametri. Uso (dalla cartella motore-grafico, dopo mvn compile):
 *   java -cp "target\classes;<json.jar>" src.SimRunner [seed] [tick] [velocita'] [saveload]
 * Argomenti (tutti opzionali): seed, numero di tick da simulare, fattore di velocita'; poi, in
 * qualsiasi ordine: "saveload" (a fine corsa prova Save + Load), "riserva=<n>" (riserva massima
 * di cibo delle entita', standard 200), "dieta=0" (la dieta non vale per mangiare, come prima).
 * Le fondatrici partono sempre dagli istinti di base (vedi entity.seedInstincts).
 * Scrive logs/stats-session-N.csv e genealogy/session-N.json come la simulazione normale.
 */
public class SimRunner {

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42;
        long ticks = args.length > 1 ? Long.parseLong(args[1]) : 3000;
        double scale = args.length > 2 ? Double.parseDouble(args[2]) : Simulation.MAX_TIME_SCALE;
        boolean saveLoad = false;
        for (int i = 3; i < args.length; i++) {
            if (args[i].equals("saveload")) {
                saveLoad = true;
            } else if (args[i].startsWith("riserva=")) {
                entity.setMaxFood(Integer.parseInt(args[i].substring(8)));
            } else if (args[i].startsWith("dieta=")) {
                // dieta=0 e' il braccio di controllo: l'abitudine alimentare non si sposta mai
                // dal gene, cioe' la dieta resta l'etichetta fissa di prima. Qualunque altro
                // valore e' quanto un singolo pasto sposta l'abitudine (normale: 0.02).
                ActionWeights.dietAdapt = Double.parseDouble(args[i].substring(6));
            } else if (args[i].startsWith("richiesta=")) {
                // richiesta=0: il vecchio accoppiamento immediato, senza il si' dell'altra parte
                ActionWeights.mateRequest = Double.parseDouble(args[i].substring(10));
            } else if (args[i].startsWith("archivio=")) {
                // archivio=0: le fondatrici ripartono dal seme a tavolino, senza il registratore
                // di movimento delle sessioni precedenti (vedi src.body.GaitArchive)
                ActionWeights.gaitArchive = Double.parseDouble(args[i].substring(9));
                src.body.GaitArchive.enabled = ActionWeights.gaitArchive > 0;
            } else if (args[i].startsWith("riflesso=")) {
                // riflesso=0: solo oscillatore a ciclo aperto, il braccio di controllo del
                // movimento appreso (vedi src.body.Reflex)
                src.body.Gait.useReflex = Double.parseDouble(args[i].substring(9)) > 0;
            } else if (args[i].startsWith("oscillatore=")) {
                // oscillatore=0: l'oscillatore diventa solo un orologio e il movimento lo deve
                // generare tutto il riflesso (vedi src.body.Gait.oscWeight)
                src.body.Gait.oscWeight = Double.parseDouble(args[i].substring(12));
            } else if (args[i].startsWith("avvicina=")) {
                // avvicina=n: peso dell'istinto "qualcuno davanti e' un motivo per camminare"
                // (vedi ActionWeights.n_moveMate). 0 = spento, come prima.
                ActionWeights.n_moveMate = Double.parseDouble(args[i].substring(9));
            } else if (args[i].startsWith("fisica=")) {
                // fisica=n: n entita' camminano in fisica vera dentro la simulazione (src.body.Palco)
                ActionWeights.fisicaViva = Double.parseDouble(args[i].substring(7));
            } else if (args[i].startsWith("svezzamento=")) {
                // svezzamento=0: chi non sa ancora camminare paga il consumo pieno, come prima
                ActionWeights.svezzamento = Double.parseDouble(args[i].substring(12));
            } else if (args[i].startsWith("vedere=")) {
                // vedere=0: la maschera delle azioni non chiede di vedere il bersaglio (come
                // prima, con le otto celle intorno per l'acqua e il partner a 360 gradi)
                ActionWeights.maskSeen = Double.parseDouble(args[i].substring(7));
            }
        }
        System.out.println("---- Opzioni: dieta=" + ActionWeights.dietAdapt
                + " vedere=" + ActionWeights.maskSeen
                + " richiesta=" + ActionWeights.mateRequest
                + " archivio=" + ActionWeights.gaitArchive
                + " svezzamento=" + ActionWeights.svezzamento
                + " fisica=" + ActionWeights.fisicaViva
                + " avvicina=" + ActionWeights.n_moveMate
                + " riflesso=" + (src.body.Gait.useReflex ? 1 : 0)
                + " oscillatore=" + src.body.Gait.oscWeight
                + " riserva=" + entity.getMaxFood());

        Simulation.setSeed(seed);
        // vedi StatsLog.open: una corsa senza log non va misurata, e soprattutto non va
        // silenziosamente conteggiata in un lotto appaiato
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (StatsLog.aperturaFallita()) {
                System.out.println("ERRORE: questa corsa non ha prodotto log, non usarla.");
            }
        }));
        Simulation.setTimeScale(scale);
        console.startSimulation(true);

        while (Simulation.isRunning() && Simulation.getTick() < ticks) {
            Thread.sleep(100);
        }

        System.out.println("---- Fine: tick " + Simulation.getTick());
        System.out.println(Genealogy.summary());
        System.out.println(src.body.Locomotion.stats());
        System.out.println(src.body.Locomotion.practiceStats());
        System.out.println(src.body.GaitArchive.stats());
        if (ActionWeights.fisicaViva > 0) {
            System.out.println(src.body.Palco.stats());
        }
        System.out.println("Entita' vive: " + Entity_manager.Entity_count());

        if (saveLoad) {
            Simulation.setTimeScale(1);
            world.PauseCycle(true);
            Thread.sleep(700);
            List<entity> before = Entity_manager.snapshotAlive();
            double sumHealth = 0, sumSpeed = 0;
            int sumGen = 0;
            for (entity e : before) {
                sumHealth += e.getStats().maxHealth;
                sumSpeed += e.getStats().speed;
                sumGen += e.getGenome().lineage().generation();
            }
            long tickBefore = Simulation.getTick();

            System.out.println("Save: " + Saving.Save());
            File[] dirs = new File("saving/").listFiles(File::isDirectory);
            System.out.println("Cartelle di salvataggio: " + (dirs == null ? 0 : dirs.length));
            System.out.println("Load: " + Saving.Load(dirs.length - 1));

            Thread.sleep(500);
            List<entity> after = Entity_manager.snapshotAlive();
            double sumHealth2 = 0, sumSpeed2 = 0;
            int sumGen2 = 0;
            for (entity e : after) {
                sumHealth2 += e.getStats().maxHealth;
                sumSpeed2 += e.getStats().speed;
                sumGen2 += e.getGenome().lineage().generation();
            }
            System.out.println("Prima:  n=" + before.size() + " maxHealth=" + sumHealth + " speed=" + sumSpeed + " gen=" + sumGen + " tick=" + tickBefore);
            System.out.println("Dopo:   n=" + after.size() + " maxHealth=" + sumHealth2 + " speed=" + sumSpeed2 + " gen=" + sumGen2 + " tick=" + Simulation.getTick());
            System.out.println(Genealogy.summary());
            Thread.sleep(1500);
            System.out.println("Dopo altri tick: n=" + Entity_manager.Entity_count() + " tick=" + Simulation.getTick());
        }

        Simulation.stop();
        Genealogy.save();
        System.exit(0);
    }
}
