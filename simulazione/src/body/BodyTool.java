package src.body;

import src.genetics.BodyPlan;
import src.genetics.Genome;
import src.genetics.GenomeSchema;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Genera i file URDF + JSON per l'addestramento della locomozione: un umanoide di riferimento, un serpente
 * e N corpi ottenuti facendo evolvere genomi casuali per qualche generazione con mutazioni strutturali
 * frequenti (cosi' si vedono corpi con arti in meno, in piu', catene di segmenti...).
 *
 * Uso (dalla cartella motore-grafico, dopo mvn -q compile):
 *   java -cp "target\classes;C:\Users\anton\.m2\repository\org\json\json\20240303\json-20240303.jar" src.body.BodyTool <cartellaOut> [N=20] [seed=1] [generazioni=6]
 */
public final class BodyTool {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "body-out");
        int n = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 1L;
        int generations = args.length > 3 ? Integer.parseInt(args[3]) : 6;
        Files.createDirectories(out);

        GenomeSchema schema = GenomeSchema.loadFromClasspath("/gene_schema.json");
        write(out, "esit_reference", Automaton.humanoid(BodyParams.midpoint(schema)));
        write(out, "esit_snake", Automaton.fromPlan(BodyPlan.snake(14, 0.4, 0.08), 1.0));

        Genome.BODY_MUTATION = BodyPlan.MutationRates.WILD;
        for (int k = 0; k < n; k++) {
            Random rng = new Random(seed * 1000 + k);
            Genome g = Genome.random(schema, rng, "body" + k, 0);
            for (int gen = 0; gen < generations; gen++) {
                Genome partner = Genome.random(schema, rng, "p" + k + "_" + gen, 0);
                g = Genome.reproduce(g, partner, schema, rng, "body" + k + "_" + gen, gen);
            }
            write(out, String.format("esit_genome_%03d", k), Automaton.fromPlan(g.bodyPlan(schema), 1.0));
        }
        System.out.println("Scritti " + (n + 2) + " corpi in " + out.toAbsolutePath());
    }

    private static void write(Path dir, String name, Automaton a) throws Exception {
        Files.writeString(dir.resolve(name + ".urdf"), BodyExport.urdf(a, name));
        Files.writeString(dir.resolve(name + ".json"), BodyExport.spec(a, name).toString(2));
    }
}
