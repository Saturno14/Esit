package src;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import src.brain.NeuralNetwork;

/**
 * Pesi "addestrati": durante una simulazione di addestramento (riserva di cibo molto alta,
 * comando console "train") si aspetta che le entita' comincino a mangiare con regolarita' e a
 * quel punto si copiano i cervelli di quelle che hanno mangiato piu' mele in
 * trained/brains.json. Le simulazioni successive fanno partire le fondatrici da quei pesi
 * (con una piccola mutazione per varieta') invece che dagli istinti scritti a mano.
 */
public final class TrainedBrains {

    private static final Path DIR = Path.of("trained");
    private static final Path FILE = DIR.resolve("brains.json");
    private static final Path PREV = DIR.resolve("brains.prev.json");

    /** Quanti cervelli salvare (le entita' che hanno mangiato piu' mele). */
    public static final int TOP_N = 10;
    /** "Mangiare regolarmente": almeno REGULAR_FRACTION delle vive ha mangiato REGULAR_APPLES mele. */
    private static final int REGULAR_APPLES = 8;
    private static final double REGULAR_FRACTION = 0.25;
    private static final int MIN_REGULAR_ENTITIES = 5;
    private static final int CHECK_INTERVAL_TICKS = 50;
    /** Mutazione applicata a ogni copia data a una fondatrice (bassa: si parte da pesi buoni). */
    private static final double FOUNDER_MUTATION = 0.05;

    private static volatile boolean enabled = true;
    private static volatile boolean armed = false;
    private static volatile boolean captured = false;
    private static List<NeuralNetwork> cache = null;

    private TrainedBrains() {
    }

    // ------------------------------------------------------------------ stato

    /** Se false le fondatrici usano sempre gli istinti (utile per confronti A/B). */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Attiva la cattura automatica (simulazione di addestramento). */
    public static void arm() {
        armed = true;
        captured = false;
    }

    public static void disarm() {
        armed = false;
    }

    public static boolean isArmed() {
        return armed;
    }

    /** True se la cattura automatica e' gia' avvenuta in questa simulazione. */
    public static boolean wasCaptured() {
        return captured;
    }

    public static boolean exists() {
        return Files.exists(FILE);
    }

    /** Le fondatrici partono dai pesi salvati solo se attivo, presenti e non in addestramento. */
    public static boolean useForFounders() {
        return enabled && !armed && !loadAll().isEmpty();
    }

    // ------------------------------------------------------------------ cattura

    /** Chiamato dal ciclo centrale a ogni tick: se armato, controlla se e' ora di catturare. */
    public static void checkCapture(long tick) {
        if (!armed || captured || tick % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        List<entity> alive = Entity_manager.snapshotAlive();
        if (alive.isEmpty()) {
            return;
        }
        int regular = 0;
        int maxApples = 0;
        long totalApples = 0;
        for (entity e : alive) {
            int a = e.getApplesEaten();
            totalApples += a;
            maxApples = Math.max(maxApples, a);
            if (a >= REGULAR_APPLES) {
                regular++;
            }
        }
        if (tick % 500 == 0) {
            System.out.println("[addestramento] tick " + tick + ": vive=" + alive.size()
                    + ", con >=" + REGULAR_APPLES + " mele=" + regular
                    + ", max mele mangiate=" + maxApples
                    + ", media=" + String.format(java.util.Locale.ROOT, "%.2f", totalApples / (double) alive.size()));
        }
        int needed = Math.max(MIN_REGULAR_ENTITIES, (int) Math.ceil(alive.size() * REGULAR_FRACTION));
        if (regular >= needed) {
            System.out.println("[addestramento] " + regular + "/" + alive.size()
                    + " entita' hanno mangiato almeno " + REGULAR_APPLES + " mele: catturo i pesi");
            String result = captureNow(tick);
            System.out.println("[addestramento] " + result);
            captured = true;
        }
    }

    /** Salva i cervelli delle TOP_N entita' vive che hanno mangiato piu' mele. Ritorna un messaggio. */
    public static synchronized String captureNow(long tick) {
        List<entity> ranked = new ArrayList<>();
        for (entity e : Entity_manager.snapshotAlive()) {
            if (e.getApplesEaten() > 0) {
                ranked.add(e);
            }
        }
        if (ranked.isEmpty()) {
            return "Nessuna entita' viva ha ancora mangiato mele: niente da salvare";
        }
        ranked.sort(Comparator.comparingInt(entity::getApplesEaten).reversed()
                .thenComparing(Comparator.comparingDouble(entity::getFitness).reversed()));
        List<entity> top = ranked.subList(0, Math.min(TOP_N, ranked.size()));

        JSONArray brains = new JSONArray();
        StringBuilder summary = new StringBuilder();
        for (entity e : top) {
            JSONObject o = new JSONObject();
            o.put("id", e.getId());
            o.put("applesEaten", e.getApplesEaten());
            o.put("brain", BrainIO.toJson(e.getBestBrain()));
            brains.put(o);
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append("#").append(e.getId()).append("(").append(e.getApplesEaten()).append(")");
        }
        JSONObject root = new JSONObject();
        root.put("savedAtTick", tick);
        root.put("maxFoodUsed", entity.getMaxFood());
        root.put("topology", new JSONArray(entity.BRAIN_TOPOLOGY));
        root.put("brains", brains);

        try {
            Files.createDirectories(DIR);
            if (Files.exists(FILE)) {
                Files.move(FILE, PREV, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(FILE, root.toString(2));
        } catch (IOException ex) {
            return "Errore salvataggio pesi: " + ex.getMessage();
        }
        cache = null;
        return "Salvati " + top.size() + " cervelli in " + FILE + " (mele mangiate: " + summary + ")";
    }

    // ------------------------------------------------------------------ caricamento

    private static synchronized List<NeuralNetwork> loadAll() {
        if (cache != null) {
            return cache;
        }
        List<NeuralNetwork> list = new ArrayList<>();
        if (Files.exists(FILE)) {
            try {
                JSONObject root = new JSONObject(Files.readString(FILE));
                JSONArray arr = root.getJSONArray("brains");
                for (int i = 0; i < arr.length(); i++) {
                    NeuralNetwork n = BrainIO.fromJson(arr.getJSONObject(i).getJSONObject("brain"));
                    if (Arrays.equals(n.getTopology(), entity.BRAIN_TOPOLOGY)) {
                        list.add(n);
                    } else {
                        System.out.println("Pesi addestrati ignorati: topologia diversa "
                                + Arrays.toString(n.getTopology()));
                    }
                }
            } catch (Exception ex) {
                System.out.println("Errore lettura pesi addestrati: " + ex.getMessage());
            }
        }
        cache = list;
        return list;
    }

    public static int count() {
        return loadAll().size();
    }

    /** Copia (leggermente mutata) del cervello numero index, a rotazione. */
    public static NeuralNetwork founderBrain(int index) {
        List<NeuralNetwork> all = loadAll();
        NeuralNetwork copy = all.get(index % all.size()).copy();
        copy.mutate(FOUNDER_MUTATION);
        return copy;
    }
}
