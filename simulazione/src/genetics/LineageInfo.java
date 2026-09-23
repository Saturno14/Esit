package src.genetics;

/**
 * Blocco "lineage" di un genoma (GENETICS_SPEC.md punto 8). Puramente informativo per
 * analisi post-hoc: non influenza mai il calcolo del fenotipo o la riproduzione.
 * parentAId/parentBId sono null solo per entita' fondatrici generate casualmente.
 */
public record LineageInfo(String entityId, String parentAId, String parentBId,
                           int generation, long birthTick) {
}
