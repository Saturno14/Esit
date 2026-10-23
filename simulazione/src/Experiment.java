package src;

import java.lang.reflect.Method;

/**
 * Cassa di prova per confrontare bilanciamenti: avanza la simulazione SENZA le attese del ritmo reale
 * (con il tetto di velocita' a 50, SimRunner fa al massimo ~100 tick al secondo), chiamando tick() in
 * un ciclo. Una simulazione per processo (lo stato e' statico).
 *
 *   java -cp "target\classes;<json.jar>" src.Experiment [embodied|structured|balanced|legacy] [seed=1] [tick=6000] [campo=valore,...]
 *
 * "campo=valore" sovrascrive un campo di ActionWeights (es. mateBias=-6,eatApple=8). Scrive le stesse
 * righe "[tick N] ..." della simulazione e alla fine una riga "RISULTATO ..." facile da leggere con uno script.
 * Si lancia da una cartella vuota: scrive logs/ e genealogy/ come SimRunner.
 */
public final class Experiment {

    public static void main(String[] args) throws Exception {
        String preset = args.length > 0 ? args[0] : "structured";
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 1;
        long ticks = args.length > 2 ? Long.parseLong(args[2]) : 6000;
        switch (preset) {
            case "legacy" -> ActionWeights.legacy();
            case "balanced" -> ActionWeights.balanced();
            case "structured" -> ActionWeights.structured();
            default -> ActionWeights.embodied();
        }
        if (args.length > 3 && !args[3].isEmpty()) {
            for (String kv : args[3].split(",")) {
                String[] p = kv.split("=");
                ActionWeights.class.getField(p[0].trim()).setDouble(null, Double.parseDouble(p[1].trim()));
            }
        }
        // quinto argomento: "niente-allenamento" spegne l'allenamento durante la vita, per il
        // confronto A/B (vedi src.body.Locomotion.practice)
        for (String opt : args.length > 4 ? args[4].split(",") : new String[0]) {
            if (opt.equals("niente-allenamento")) {
                src.body.Locomotion.lifetimeLearning = false;
            } else if (opt.startsWith("prove-figli=")) {
                src.body.Locomotion.trialsPerChild = Integer.parseInt(opt.substring(12));
            } else if (opt.startsWith("prove-corpo=")) {
                src.body.Locomotion.trialsPerBody = Integer.parseInt(opt.substring(12));
            } else if (opt.startsWith("thread=")) {
                src.body.Locomotion.workers = Integer.parseInt(opt.substring(7));
            } else if (opt.startsWith("riserva=")) {
                // riserva di cibo delle entita' (standard 200): leva indipendente, resta
                entity.setMaxFood(Integer.parseInt(opt.substring(8)));
            } else if (!opt.isEmpty()) {
                throw new IllegalArgumentException("opzione sconosciuta: " + opt);
            }
        }
        Simulation.setSeed(seed);
        console.startSimulation(true);
        Simulation.stop();            // fermo il thread con il ritmo reale: i tick li faccio io
        Thread.sleep(300);
        Method tick = Simulation.class.getDeclaredMethod("tick");
        tick.setAccessible(true);
        boolean extinct = false;
        for (long i = 0; i < ticks; i++) {
            synchronized (Simulation.LOCK) {
                tick.invoke(null);
            }
            if (Entity_manager.Entity_count() == 0) {
                extinct = true;
                break;
            }
        }
        System.out.println("RISULTATO preset=" + preset + " seed=" + seed + " tick=" + Simulation.getTick()
                + " vivi=" + Entity_manager.Entity_count() + " estinta=" + extinct);
        System.out.println(src.body.Locomotion.stats());
        System.out.println(src.body.Locomotion.practiceStats());
        Runtime rt = Runtime.getRuntime();
        System.gc();
        System.out.println("memoria: usata " + (rt.totalMemory() - rt.freeMemory()) / (1 << 20)
                + " MiB, riservata " + rt.totalMemory() / (1 << 20)
                + " MiB, tetto " + rt.maxMemory() / (1 << 20) + " MiB, core " + rt.availableProcessors());
        System.out.println(bodySummary());
        System.exit(0);
    }

    /**
     * Media delle misure del corpo sui vivi: se la selezione sta lavorando sulla morfologia,
     * questi numeri si spostano rispetto alla popolazione iniziale (vedi la riga CORPO del tick 0
     * nei log). Serve a dire se l'incarnazione CONTA, non solo se la popolazione sopravvive.
     */
    private static String bodySummary() {
        double mass = 0, leg = 0, reach = 0, speed = 0;
        int n = 0, maxGen = 0, measured = 0;
        for (entity e : Entity_manager.snapshotAlive()) {
            if (!e.isAlive()) {
                continue;
            }
            src.body.BodyTraits t = e.getTraits();
            mass += t.mass();
            leg += t.legLength();
            reach += t.reach();
            speed += e.getWalkSpeed();
            if (e.getLocomotion().measured()) {
                measured++;
            }
            maxGen = Math.max(maxGen, e.getGenome().lineage().generation());
            n++;
        }
        if (n == 0) {
            return "CORPO nessun vivo";
        }
        return String.format(java.util.Locale.ROOT,
                "CORPO vivi=%d gen_max=%d massa=%.1f kg gamba=%.3f m portata=%.3f m velocita'=%.3f m/s misurati=%d/%d",
                n, maxGen, mass / n, leg / n, reach / n, speed / n, measured, n);
    }
}
