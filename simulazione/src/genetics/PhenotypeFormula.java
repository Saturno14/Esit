package src.genetics;

import java.util.List;

/**
 * Una entry di "phenotypeFormulas" (GENETICS_SPEC.md punto 5): la stat "statId" e' la
 * media pesata dei geni elencati in "terms". Se un gene non ha una formula dichiarata
 * nello schema, PhenotypeResolver usa identity(id): il gene risolve su se stesso.
 */
public final class PhenotypeFormula {

    public static final class Term {
        public final String geneId;
        public final double weight;

        public Term(String geneId, double weight) {
            this.geneId = geneId;
            this.weight = weight;
        }
    }

    public final String statId;
    public final List<Term> terms;

    public PhenotypeFormula(String statId, List<Term> terms) {
        this.statId = statId;
        this.terms = List.copyOf(terms);
    }

    public static PhenotypeFormula identity(String geneId) {
        return new PhenotypeFormula(geneId, List.of(new Term(geneId, 1.0)));
    }
}
