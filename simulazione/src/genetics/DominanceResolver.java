package src.genetics;

/**
 * Funzione pura (GENETICS_SPEC.md punto 1.2): risolve una coppia di alleli in un singolo
 * valore normalizzato [0,1] secondo la regola di dominanza del gene.
 */
public final class DominanceResolver {

    private DominanceResolver() {
    }

    public static double resolve(Allele allele, GeneDefinition.DominanceMode mode) {
        return switch (mode) {
            case ADDITIVE -> (allele.alleleA + allele.alleleB) / 2.0;
            case DOMINANT_HIGH -> Math.max(allele.alleleA, allele.alleleB);
            case DOMINANT_LOW -> Math.min(allele.alleleA, allele.alleleB);
        };
    }
}
