package src.genetics;

/**
 * Tipo di dieta risolto dalle soglie su "behavior.diet_bias" (gia' mappato in unita' reali
 * [0,1] da PhenotypeResolver, quindi qui il valore in ingresso e' gia' pronto all'uso).
 */
public enum DietType {
    HERBIVORE,
    OMNIVORE,
    CARNIVORE;

    public static DietType fromResolvedBias(double resolvedDietBias) {
        if (resolvedDietBias < 0.33) return HERBIVORE;
        if (resolvedDietBias < 0.66) return OMNIVORE;
        return CARNIVORE;
    }
}
