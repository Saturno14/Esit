package src;

import java.lang.reflect.Method;

/**
 * Cassa di prova per confrontare bilanciamenti: avanza la simulazione SENZA le attese del ritmo reale
 * (con il tetto di velocita' a 50, SimRunner fa al massimo ~100 tick al secondo), chiamando tick() in
 * un ciclo. Una simulazione per processo (lo stato e' statico).
 *
 *   java -cp "target\classes;<json.jar>" src.Experiment [structured|balanced|legacy] [seed=1] [tick=6000] [campo=valore,...]
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
            default -> ActionWeights.structured();
        }
        if (args.length > 3 && !args[3].isEmpty()) {
            for (String kv : args[3].split(",")) {
                String[] p = kv.split("=");
                ActionWeights.class.getField(p[0].trim()).setDouble(null, Double.parseDouble(p[1].trim()));
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
        System.exit(0);
    }
}
