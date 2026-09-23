package src.genetics;

import java.util.Random;

/**
 * Mutazione gaussiana + salto raro (GENETICS_SPEC.md punto 7), applicata indipendentemente
 * a ciascun allele del figlio, dopo il crossover.
 */
public final class MutationService {

    /** Costante globale, non per gene (punto 7.2). */
    public static final double RARE_JUMP_CHANCE = 0.01;

    private MutationService() {
    }

    public static double mutateAllele(double inheritedValue, GeneDefinition def, Random rng) {
        double value = inheritedValue;

        // 1. Mutazione gaussiana, con probabilita' gene.mutationChance
        if (rng.nextDouble() < def.mutationChance) {
            value += rng.nextGaussian() * def.mutationStdDev;
            value = Allele.clamp(value);
        }

        // 2. Salto raro: ignora il valore ereditato e ricampiona uniformemente in [0,1]
        if (rng.nextDouble() < RARE_JUMP_CHANCE) {
            value = rng.nextDouble();
        }

        return value;
    }
}
