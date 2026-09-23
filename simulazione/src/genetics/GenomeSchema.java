package src.genetics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Carica e valida gene_schema.json (GENETICS_SPEC.md punti 4, 5, 6, 9.1). Una sola istanza
 * condivisa da tutte le entita'. La validazione avviene una volta al boot: qualunque
 * violazione fa fallire l'avvio con un'eccezione (mai un warning silenzioso).
 */
public final class GenomeSchema {

    private final int schemaVersion;
    private final Map<String, GeneDefinition> genes;
    private final Map<String, PhenotypeFormula> formulas;

    private GenomeSchema(int schemaVersion, Map<String, GeneDefinition> genes,
                          Map<String, PhenotypeFormula> formulas) {
        this.schemaVersion = schemaVersion;
        this.genes = Collections.unmodifiableMap(genes);
        this.formulas = Collections.unmodifiableMap(formulas);
    }

    public static GenomeSchema loadFromClasspath(String resourcePath) {
        try (InputStream in = GenomeSchema.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Schema genetico non trovato sul classpath: " + resourcePath);
            }
            String content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return parseAndValidate(new JSONObject(content));
        } catch (IOException e) {
            throw new IllegalStateException("Errore lettura schema genetico: " + resourcePath, e);
        }
    }

    public static GenomeSchema loadFromFile(Path path) {
        try {
            String content = Files.readString(path);
            return parseAndValidate(new JSONObject(content));
        } catch (IOException e) {
            throw new IllegalStateException("Errore lettura schema genetico: " + path, e);
        }
    }

    private static GenomeSchema parseAndValidate(JSONObject root) {
        int schemaVersion = root.getInt("schemaVersion");

        Map<String, GeneDefinition> genes = new LinkedHashMap<>();
        JSONArray genesArr = root.getJSONArray("genes");
        for (int i = 0; i < genesArr.length(); i++) {
            JSONObject g = genesArr.getJSONObject(i);
            String id = g.getString("id");

            // Punto 6: id con prefisso categoria valido (es. "body.torso_scale")
            int dot = id.indexOf('.');
            if (dot <= 0 || dot == id.length() - 1) {
                throw new IllegalStateException(
                        "Gene '" + id + "': id senza prefisso categoria valido (atteso 'categoria.nome').");
            }

            double realMin = g.getDouble("realMin");
            double realMax = g.getDouble("realMax");
            if (!(realMin < realMax)) {
                throw new IllegalStateException("Gene '" + id + "': realMin deve essere < realMax.");
            }

            double defaultValue = g.optDouble("defaultValue", 0.5);
            double mutationStdDev = g.optDouble("mutationStdDev", 0.05);
            double mutationChance = g.optDouble("mutationChance", 0.10);

            String domRaw = g.optString("dominanceMode", "additive");
            GeneDefinition.DominanceMode mode = parseDominance(domRaw, id);

            List<String> affects = new ArrayList<>();
            if (g.has("affects")) {
                JSONArray aff = g.getJSONArray("affects");
                for (int k = 0; k < aff.length(); k++) {
                    affects.add(aff.getString(k));
                }
            }

            genes.put(id, new GeneDefinition(id, realMin, realMax, defaultValue,
                    mutationStdDev, mutationChance, mode, affects));
        }

        Map<String, PhenotypeFormula> formulas = new LinkedHashMap<>();
        if (root.has("phenotypeFormulas")) {
            JSONObject formulasObj = root.getJSONObject("phenotypeFormulas");
            for (String statId : formulasObj.keySet()) {
                JSONObject entry = formulasObj.getJSONObject(statId);
                JSONArray terms = entry.getJSONArray("terms");
                List<PhenotypeFormula.Term> termList = new ArrayList<>();
                for (int i = 0; i < terms.length(); i++) {
                    JSONObject t = terms.getJSONObject(i);
                    String geneId = t.getString("gene");
                    double weight = t.getDouble("weight");

                    // Punto 6: ogni gene referenziato in phenotypeFormulas deve esistere
                    if (!genes.containsKey(geneId)) {
                        throw new IllegalStateException("phenotypeFormulas['" + statId
                                + "'] referenzia il gene inesistente '" + geneId + "'.");
                    }
                    termList.add(new PhenotypeFormula.Term(geneId, weight));
                }
                formulas.put(statId, new PhenotypeFormula(statId, termList));
            }
        }

        return new GenomeSchema(schemaVersion, genes, formulas);
    }

    private static GeneDefinition.DominanceMode parseDominance(String raw, String geneId) {
        return switch (raw) {
            case "additive" -> GeneDefinition.DominanceMode.ADDITIVE;
            case "dominant_high" -> GeneDefinition.DominanceMode.DOMINANT_HIGH;
            case "dominant_low" -> GeneDefinition.DominanceMode.DOMINANT_LOW;
            default -> throw new IllegalStateException(
                    "Gene '" + geneId + "': dominanceMode '" + raw + "' non valido.");
        };
    }

    public GeneDefinition get(String id) {
        GeneDefinition def = genes.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Gene non definito nello schema: " + id);
        }
        return def;
    }

    public GeneDefinition tryGet(String id) {
        return genes.get(id);
    }

    public Collection<GeneDefinition> allGenes() {
        return genes.values();
    }

    /** Punto 4: iterazione dei geni filtrati per prefisso, es. schema.allWithPrefix("body."). */
    public List<GeneDefinition> allWithPrefix(String prefix) {
        List<GeneDefinition> out = new ArrayList<>();
        for (GeneDefinition def : genes.values()) {
            if (def.id.startsWith(prefix)) {
                out.add(def);
            }
        }
        return out;
    }

    /** Null se il gene non ha una formula dichiarata: il chiamante ricade su identity(id). */
    public PhenotypeFormula formulaFor(String statId) {
        return formulas.get(statId);
    }

    public int schemaVersion() {
        return schemaVersion;
    }
}
