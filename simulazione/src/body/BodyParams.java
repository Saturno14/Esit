package src.body;

import src.genetics.Genome;
import src.genetics.GenomeSchema;
import src.genetics.PhenotypeResolver;

/**
 * Proporzioni del corpo in metri (valori fenotipici body.* gia' risolti) piu' il fattore di
 * crescita. E' l'unico ingresso di {@link Automaton#humanoid}: il corpo non legge mai il genoma
 * direttamente, passa sempre da PhenotypeResolver.
 */
public record BodyParams(double torso, double neck, double head, double limbLength,
                         double limbThickness, double hand, double finger, double foot,
                         double growth) {

    /** Come GenomeMeshApplier.MATURITY_AGE / BABY_SCALE (la grafica usa 200, entity.MATURITY_AGE vale 100). */
    public static final int GROWTH_AGE = 200;
    public static final double BABY_SCALE = 0.35;

    public static double growthFactor(int age) {
        double t = Math.min(1.0, Math.max(0.0, age / (double) GROWTH_AGE));
        return BABY_SCALE + (1.0 - BABY_SCALE) * t;
    }

    /** Chiave per sapere se il corpo va ricostruito: cambia finche' l'entita' cresce. */
    public static int growthKey(int age) { return Math.min(Math.max(age, 0), GROWTH_AGE); }

    public static BodyParams fromGenome(Genome genome, GenomeSchema schema, int age) {
        return new BodyParams(
                PhenotypeResolver.resolve("body.torso_scale", genome, schema),
                PhenotypeResolver.resolve("body.neck_length", genome, schema),
                PhenotypeResolver.resolve("body.head_scale", genome, schema),
                PhenotypeResolver.resolve("body.limb_length", genome, schema),
                PhenotypeResolver.resolve("body.limb_thickness", genome, schema),
                PhenotypeResolver.resolve("body.hand_length", genome, schema),
                PhenotypeResolver.resolve("body.finger_length", genome, schema),
                PhenotypeResolver.resolve("body.foot_length", genome, schema),
                growthFactor(age));
    }

    /** Adulto "medio": punto centrale dell'intervallo di ogni gene body.* (utile per test e URDF di riferimento). */
    public static BodyParams midpoint(GenomeSchema schema) {
        return new BodyParams(mid(schema, "body.torso_scale"), mid(schema, "body.neck_length"),
                mid(schema, "body.head_scale"), mid(schema, "body.limb_length"),
                mid(schema, "body.limb_thickness"), mid(schema, "body.hand_length"),
                mid(schema, "body.finger_length"), mid(schema, "body.foot_length"), 1.0);
    }

    private static double mid(GenomeSchema schema, String id) {
        return (schema.get(id).realMin + schema.get(id).realMax) / 2.0;
    }
}
