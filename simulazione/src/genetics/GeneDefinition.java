package src.genetics;

import java.util.List;

/**
 * Un gene dello schema (GENETICS_SPEC.md punto 9.1): metadati statici letti una volta da
 * gene_schema.json e condivisi da tutte le entita'. Nessuna logica di gioco qui dentro.
 */
public final class GeneDefinition {

    public enum DominanceMode {
        ADDITIVE,
        DOMINANT_HIGH,
        DOMINANT_LOW
    }

    public final String id;
    public final double realMin;
    public final double realMax;
    public final double defaultValue; // spazio normalizzato [0,1]
    public final double mutationStdDev;
    public final double mutationChance;
    public final DominanceMode dominanceMode;
    public final List<String> affects;

    public GeneDefinition(String id, double realMin, double realMax, double defaultValue,
                           double mutationStdDev, double mutationChance,
                           DominanceMode dominanceMode, List<String> affects) {
        this.id = id;
        this.realMin = realMin;
        this.realMax = realMax;
        this.defaultValue = defaultValue;
        this.mutationStdDev = mutationStdDev;
        this.mutationChance = mutationChance;
        this.dominanceMode = dominanceMode;
        this.affects = List.copyOf(affects);
    }
}
