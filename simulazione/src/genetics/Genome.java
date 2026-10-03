package src.genetics;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Genotipo (GENETICS_SPEC.md punto 3): solo dati grezzi, coppie di alleli normalizzati
 * [0,1] per ogni gene, piu' il blocco lineage. Nessuna logica di gioco, nessun riferimento
 * a mesh o stat: quello e' compito di PhenotypeResolver / EntityStats / GenomeMeshApplier.
 * Immutabile dopo la creazione.
 */
public final class Genome {

    private final Map<String, Allele> genes;
    private final LineageInfo lineage;
    private final int schemaVersion;

    private Genome(Map<String, Allele> genes, LineageInfo lineage, int schemaVersion) {
        this.genes = Collections.unmodifiableMap(new LinkedHashMap<>(genes));
        this.lineage = lineage;
        this.schemaVersion = schemaVersion;
    }

    public Allele get(String geneId) {
        Allele allele = genes.get(geneId);
        if (allele == null) {
            throw new IllegalArgumentException("Gene non presente nel genoma: " + geneId);
        }
        return allele;
    }

    public Set<String> geneIds() {
        return genes.keySet();
    }

    public LineageInfo lineage() {
        return lineage;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    /** Entita' fondatrice: alleleA e alleleB generati random e indipendenti (punto 1.1). */
    public static Genome random(GenomeSchema schema, Random rng, String entityId, long birthTick) {
        Map<String, Allele> map = new LinkedHashMap<>();
        for (GeneDefinition def : schema.allGenes()) {
            map.put(def.id, new Allele(rng.nextDouble(), rng.nextDouble()));
        }
        LineageInfo lineage = new LineageInfo(entityId, null, null, 0, birthTick);
        return new Genome(map, lineage, schema.schemaVersion());
    }

    /**
     * Crossover a due genitori + mutazione (punti 1.3 e 7). Per ogni gene: childAlleleA
     * viene scelto 50/50 tra i due alleli di parentA, childAlleleB 50/50 tra i due alleli
     * di parentB (mai mischiando i due alleli di uno stesso genitore nello stesso slot),
     * poi entrambi mutano indipendentemente.
     */
    public static Genome reproduce(Genome parentA, Genome parentB, GenomeSchema schema, Random rng,
                                    String childId, long birthTick) {
        Map<String, Allele> childGenes = new LinkedHashMap<>();
        for (GeneDefinition def : schema.allGenes()) {
            Allele fromA = parentA.get(def.id);
            Allele fromB = parentB.get(def.id);

            double childAlleleA = rng.nextBoolean() ? fromA.alleleA : fromA.alleleB;
            double childAlleleB = rng.nextBoolean() ? fromB.alleleA : fromB.alleleB;

            childAlleleA = MutationService.mutateAllele(childAlleleA, def, rng);
            childAlleleB = MutationService.mutateAllele(childAlleleB, def, rng);

            childGenes.put(def.id, new Allele(childAlleleA, childAlleleB));
        }

        int generation = Math.max(parentA.lineage.generation(), parentB.lineage.generation()) + 1;
        LineageInfo lineage = new LineageInfo(childId, parentA.lineage.entityId(),
                parentB.lineage.entityId(), generation, birthTick);

        return new Genome(childGenes, lineage, schema.schemaVersion());
    }

    /**
     * Punto 6: migra questo genoma allo schema corrente. Geni nuovi nello schema ma assenti
     * qui vengono aggiunti con alleleA = alleleB = defaultValue del gene. Geni presenti qui
     * ma assenti nello schema corrente vengono rimossi con un warning (non un errore fatale).
     */
    public Genome migrate(GenomeSchema currentSchema) {
        if (schemaVersion == currentSchema.schemaVersion()) {
            return this;
        }

        Map<String, Allele> migrated = new LinkedHashMap<>();
        for (GeneDefinition def : currentSchema.allGenes()) {
            Allele existing = genes.get(def.id);
            migrated.put(def.id, existing != null ? existing : new Allele(def.defaultValue, def.defaultValue));
        }
        for (String oldId : genes.keySet()) {
            if (currentSchema.tryGet(oldId) == null) {
                System.out.println("[Genome.migrate] Warning: gene '" + oldId
                        + "' non presente nello schema corrente (v" + currentSchema.schemaVersion()
                        + "), rimosso durante la migrazione.");
            }
        }

        return new Genome(migrated, lineage, currentSchema.schemaVersion());
    }

    /**
     * Deriva genetica legata all'eta' (mutazione somatica): restituisce un NUOVO genoma con
     * stesso lineage e schemaVersion, in cui ogni allele puo' spostarsi leggermente.
     * intensity in [0,1] scala sia la probabilita' sia l'ampiezza dello spostamento (piu' e'
     * vecchia l'entita', piu' deriva). La classe resta immutabile: chi possiede l'entita'
     * sostituisce il proprio riferimento al genoma con quello restituito.
     */
    public Genome ageDrift(GenomeSchema schema, Random rng, double intensity) {
        double k = Math.max(0.0, Math.min(1.0, intensity));
        Map<String, Allele> drifted = new LinkedHashMap<>();
        for (GeneDefinition def : schema.allGenes()) {
            Allele current = genes.get(def.id);
            if (current == null) {
                current = new Allele(def.defaultValue, def.defaultValue);
            }
            drifted.put(def.id, new Allele(
                    driftValue(current.alleleA, def, rng, k),
                    driftValue(current.alleleB, def, rng, k)));
        }
        return new Genome(drifted, lineage, schemaVersion);
    }

    private static double driftValue(double value, GeneDefinition def, Random rng, double k) {
        if (rng.nextDouble() < def.mutationChance * k) {
            return Allele.clamp(value + rng.nextGaussian() * def.mutationStdDev * k);
        }
        return value;
    }

    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", schemaVersion);

        JSONObject lineageObj = new JSONObject();
        lineageObj.put("entityId", lineage.entityId());
        lineageObj.put("parentAId", lineage.parentAId());
        lineageObj.put("parentBId", lineage.parentBId());
        lineageObj.put("generation", lineage.generation());
        lineageObj.put("birthTick", lineage.birthTick());
        root.put("lineage", lineageObj);

        JSONObject genesObj = new JSONObject();
        for (Map.Entry<String, Allele> entry : genes.entrySet()) {
            JSONObject a = new JSONObject();
            a.put("alleleA", entry.getValue().alleleA);
            a.put("alleleB", entry.getValue().alleleB);
            genesObj.put(entry.getKey(), a);
        }
        root.put("genes", genesObj);

        return root;
    }

    public static Genome fromJson(JSONObject obj) {
        int version = obj.getInt("schemaVersion");

        JSONObject lin = obj.getJSONObject("lineage");
        String parentA = (lin.has("parentAId") && !lin.isNull("parentAId")) ? lin.getString("parentAId") : null;
        String parentB = (lin.has("parentBId") && !lin.isNull("parentBId")) ? lin.getString("parentBId") : null;
        LineageInfo lineage = new LineageInfo(
                lin.getString("entityId"), parentA, parentB,
                lin.getInt("generation"), lin.getLong("birthTick"));

        Map<String, Allele> genes = new LinkedHashMap<>();
        JSONObject genesObj = obj.getJSONObject("genes");
        for (String id : genesObj.keySet()) {
            JSONObject a = genesObj.getJSONObject(id);
            genes.put(id, new Allele(a.getDouble("alleleA"), a.getDouble("alleleB")));
        }

        return new Genome(genes, lineage, version);
    }

    public void saveToFile(Path path) throws IOException {
        Files.writeString(path, toJson().toString(2));
    }

    /** Carica un genoma da file e lo migra subito allo schema corrente (punto 6). */
    public static Genome loadFromFile(Path path, GenomeSchema currentSchema) throws IOException {
        JSONObject obj = new JSONObject(Files.readString(path));
        return fromJson(obj).migrate(currentSchema);
    }
}
