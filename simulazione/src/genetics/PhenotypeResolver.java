package src.genetics;

/**
 * Applica phenotypeFormulas + DominanceResolver per produrre valori in unita' reali
 * (GENETICS_SPEC.md punto 5.2). Nessuna formula hardcoded: tutto letto dallo schema
 * a runtime. Usato sia da EntityStats sia da GenomeMeshApplier per i geni body.*.
 */
public final class PhenotypeResolver {

    private PhenotypeResolver() {
    }

    public static double resolve(String statId, Genome genome, GenomeSchema schema) {
        GeneDefinition targetDef = schema.get(statId);
        PhenotypeFormula formula = schema.formulaFor(statId);
        if (formula == null) {
            formula = PhenotypeFormula.identity(statId);
        }

        double weightedSum = 0;
        double weightTotal = 0;
        for (PhenotypeFormula.Term term : formula.terms) {
            GeneDefinition termDef = schema.get(term.geneId);
            Allele allele = genome.get(term.geneId);
            double normalized = DominanceResolver.resolve(allele, termDef.dominanceMode);
            weightedSum += normalized * term.weight;
            weightTotal += term.weight;
        }

        double normalizedStat = weightTotal > 0 ? weightedSum / weightTotal : 0.0;
        return targetDef.realMin + normalizedStat * (targetDef.realMax - targetDef.realMin);
    }
}
