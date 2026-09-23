package src.genetics;

/**
 * Coppia di alleli normalizzati [0,1] per un singolo gene (GENETICS_SPEC.md punto 1.1).
 * Un genoma non ha mai un valore singolo per gene: sempre due alleli, anche per
 * un'entita' fondatrice generata casualmente (in quel caso indipendenti tra loro).
 */
public final class Allele {

    public final double alleleA;
    public final double alleleB;

    public Allele(double alleleA, double alleleB) {
        this.alleleA = clamp(alleleA);
        this.alleleB = clamp(alleleB);
    }

    public static double clamp(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
