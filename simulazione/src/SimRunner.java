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
            }
        }
        System.out.println("---- Opzioni: dieta=" + ActionWeights.dietAdapt
                + " riserva=" + entity.getMaxFood());

        Simulation.setSeed(seed);
        Simulation.setTimeScale(scale);
        console.startSimulation(true);

        while (Simulation.isRunning() && Simulation.getTick() < ticks) {
            Thread.sleep(100);
        }

        System.out.println("---- Fine: tick " + Simulation.getTick());
        System.out.println(Genealogy.summary());
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
