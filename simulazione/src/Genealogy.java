package src;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Albero genealogico di una sessione, da un NewStart al successivo: ogni NewStart apre una
 * nuova sessione (nuovo albero) con le fondatrici come radici; ogni nascita aggiunge un nodo
 * con i due genitori. L'albero viene salvato in genealogy/session-N.json (autosave periodico)
 * e dentro ogni salvataggio (genealogy.json), cosi' un Load lo ripristina.
 */
public final class Genealogy {

    private static final class Node {
        int id;
        int parentA = -1;
        int parentB = -1;
        int generation;
        long birthTick;
        long deathTick = -1;
        String deathCause = "";
    }

    private static final Path DIR = Path.of("genealogy");
    private static final Map<Integer, Node> nodes = new LinkedHashMap<>();
    private static int session = 0;

    private Genealogy() {
    }

    public static synchronized int getSession() {
        return session;
    }

    /** Nuovo NewStart: albero vuoto e nuovo numero di sessione. */
    public static synchronized void newSession() {
        nodes.clear();
        int max = -1;
        File[] files = DIR.toFile().listFiles();
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (name.startsWith("session-") && name.endsWith(".json")) {
                    try {
                        max = Math.max(max, Integer.parseInt(name.substring(8, name.length() - 5)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        session = max + 1;
    }

    public static synchronized void recordFounder(entity e) {
        Node n = new Node();
        n.id = e.getId();
        n.generation = e.getGenome().lineage().generation();
        n.birthTick = Simulation.getTick();
        nodes.put(n.id, n);
    }

    public static synchronized void recordBirth(entity child, entity parentA, entity parentB) {
        Node n = new Node();
        n.id = child.getId();
        n.parentA = parentA.getId();
        n.parentB = parentB.getId();
        n.generation = child.getGenome().lineage().generation();
        n.birthTick = Simulation.getTick();
        nodes.put(n.id, n);
    }

    public static synchronized void recordDeath(entity e, String cause, long tick) {
        Node n = nodes.get(e.getId());
        if (n != null) {
            n.deathTick = tick;
            n.deathCause = cause;
        }
    }

    // ------------------------------------------------------------------ persistenza

    private static JSONObject toJson() {
        JSONObject root = new JSONObject();
        root.put("session", session);
        JSONArray arr = new JSONArray();
        for (Node n : nodes.values()) {
            JSONObject o = new JSONObject();
            o.put("id", n.id);
            o.put("parentA", n.parentA);
            o.put("parentB", n.parentB);
            o.put("generation", n.generation);
            o.put("birthTick", n.birthTick);
            o.put("deathTick", n.deathTick);
            o.put("deathCause", n.deathCause);
            arr.put(o);
        }
        root.put("nodes", arr);
        return root;
    }

    /** Salva in genealogy/session-N.json (autosave). */
    public static synchronized void save() {
        try {
            Files.createDirectories(DIR);
            Path target = DIR.resolve("session-" + session + ".json");
            Path tmp = DIR.resolve("session-" + session + ".json.tmp");
            Files.writeString(tmp, toJson().toString(1));
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.out.println("Errore salvataggio genealogia: " + e.getMessage());
        }
    }

    public static synchronized void saveTo(Path file) throws Exception {
        Files.writeString(file, toJson().toString(1));
    }

    public static synchronized void loadFrom(Path file) throws Exception {
        JSONObject root = new JSONObject(Files.readString(file));
        nodes.clear();
        session = root.optInt("session", 0);
        JSONArray arr = root.getJSONArray("nodes");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Node n = new Node();
            n.id = o.getInt("id");
            n.parentA = o.optInt("parentA", -1);
            n.parentB = o.optInt("parentB", -1);
            n.generation = o.optInt("generation", 0);
            n.birthTick = o.optLong("birthTick", 0);
            n.deathTick = o.optLong("deathTick", -1);
            n.deathCause = o.optString("deathCause", "");
            nodes.put(n.id, n);
        }
    }

    // ------------------------------------------------------------------ consultazione

    public static synchronized String summary() {
        int maxGen = 0;
        int founders = 0;
        int alive = 0;
        for (Node n : nodes.values()) {
            maxGen = Math.max(maxGen, n.generation);
            if (n.parentA < 0 && n.parentB < 0) founders++;
            if (n.deathTick < 0) alive++;
        }
        return "Sessione " + session + ": " + nodes.size() + " entita' (" + founders
                + " fondatrici, " + alive + " vive), generazione massima " + maxGen;
    }

    /** Antenati dell'entita' (fino a 4 livelli) e numero di figli. */
    public static synchronized String describe(int id) {
        if (!nodes.containsKey(id)) {
            return "Entita' " + id + " non presente nell'albero della sessione " + session;
        }
        StringBuilder sb = new StringBuilder();
        describeNode(sb, id, 0, 4);
        int children = 0;
        for (Node n : nodes.values()) {
            if (n.parentA == id || n.parentB == id) children++;
        }
        sb.append("Figli: ").append(children);
        return sb.toString();
    }

    private static void describeNode(StringBuilder sb, int id, int depth, int maxDepth) {
        Node n = nodes.get(id);
        String indent = "  ".repeat(depth);
        if (n == null) {
            sb.append(indent).append("#").append(id).append(" (sconosciuta)\n");
            return;
        }
        sb.append(indent).append("#").append(n.id)
                .append(" gen ").append(n.generation)
                .append(", nata al tick ").append(n.birthTick)
                .append(n.deathTick < 0 ? ", viva" : ", morta al tick " + n.deathTick + " (" + n.deathCause + ")")
                .append("\n");
        if (depth >= maxDepth) return;
        if (n.parentA >= 0) {
            sb.append(indent).append(" genitore A:\n");
            describeNode(sb, n.parentA, depth + 1, maxDepth);
        }
        if (n.parentB >= 0) {
            sb.append(indent).append(" genitore B:\n");
            describeNode(sb, n.parentB, depth + 1, maxDepth);
        }
    }
}
