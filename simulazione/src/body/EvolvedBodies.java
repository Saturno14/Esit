package src.body;

import src.genetics.BodyPlan;
import src.genetics.Genome;
import src.genetics.GenomeSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Misura la locomozione sui corpi che l'EVOLUZIONE produce davvero, non su forme scritte a mano.
 *
 * PERCHE' ESISTE. Il confronto in GaitEvaluator.main usa corpi costruiti a tavolino, e uno di
 * quelli - il "serpente" - non e' una forma che la simulazione possa produrre cosi': i corpi
 * nascono da una catena di incroci e mutazioni del piano (BodyPlan), con un numero di segmenti e
 * di gradi di liberta' che cambia di generazione in generazione. Un serpente, se esiste, deve
 * essere l'esito di quella catena. Misurare una forma inventata dice quanto e' bravo il
 * controllore su quella forma, non quanto e' bravo sui corpi veri.
 *
 * COSA FA. Costruisce stirpi con la stessa macchina della simulazione (Genome.random per i
 * fondatori, Genome.reproduce per i figli, quindi BodyPlan.cross + mutate con i tassi
 * Genome.BODY_MUTATION), poi misura ogni corpo con il budget di prove che la simulazione usa
 * davvero, e raggruppa i risultati per FORMA (quanti arti toccano terra), perche' e' quella che
 * decide come si cammina.
 *
 *   java -cp "classpath" src.body.EvolvedBodies [generazioni=12] [stirpi=6] [prove=4] [secondi=1.5] [seme=1]
 */
public final class EvolvedBodies {

    /** Individui per generazione in una stirpe: serve solo a dare due genitori diversi. */
    private static final int POPULATION = 6;
    /** Segmenti del fondatore umanoide: serve a dire quanti corpi hanno cambiato struttura. */
    private static final int FOUNDER_SEGMENTS = 18;

    private EvolvedBodies() {
    }

    private record Sample(int generation, int segments, int dofs, BodyTraits traits,
                          GaitEvaluator.Result result) {
    }

    public static void main(String[] args) {
        int generations = args.length > 0 ? Integer.parseInt(args[0]) : 12;
        int lineages = args.length > 1 ? Integer.parseInt(args[1]) : 6;
        int trials = args.length > 2 ? Integer.parseInt(args[2]) : Locomotion.trialsPerChild;
        double seconds = args.length > 3 ? Double.parseDouble(args[3]) : Locomotion.secondsPerTrial;
        long seed = args.length > 4 ? Long.parseLong(args[4]) : 1;
        // l'ultimo argomento "vecchio" rimette il seme dell'andatura di prima del 23/10/2026:
        // serve a confrontare i due controllori SUGLI STESSI CORPI
        Gait.useLegacySeed = args.length > 5 && args[5].equalsIgnoreCase("vecchio");
        GaitEvaluator.useBalance = !(args.length > 6 && args[6].equalsIgnoreCase("aperto"));

        PhysicsNatives.require();
        GenomeSchema schema = GenomeSchema.loadFromClasspath("/gene_schema.json");
        System.out.printf(Locale.ROOT,
                "Corpi evoluti: %d stirpi x %d generazioni, %d prove da %.1f s, seme dell'andatura %s%n"
                        + "mutazione del corpo %s%n%n",
                lineages, generations, trials, seconds,
                (Gait.useLegacySeed ? "VECCHIO" : "nuovo")
                        + (GaitEvaluator.useBalance ? ", con riflesso di equilibrio" : ", CICLO APERTO"),
                Genome.BODY_MUTATION);

        List<Sample> samples = new ArrayList<>();
        long t0 = System.nanoTime();
        try (GaitEvaluator ev = new GaitEvaluator()) {
            for (int line = 0; line < lineages; line++) {
                // DUE generatori distinti, e non e' un dettaglio: con uno solo, cambiare il
                // codice dell'andatura sposta anche i numeri casuali che generano i CORPI, e il
                // confronto prima/dopo non vale piu' niente (succedeva: il campione passava dal
                // 40% al 63% di corpi diversi dal fondatore solo cambiando il seme dell'andatura)
                Random bodyRng = new Random(seed * 1000L + line);
                Random gaitRng = new Random(seed * 7919L + line);
                samples.addAll(runLineage(ev, schema, bodyRng, gaitRng, generations, trials, seconds));
            }
        }
        report(samples, generations, (System.nanoTime() - t0) / 1e9);
        System.exit(0);
    }

    /**
     * Una stirpe: fondatori casuali, poi una generazione dopo l'altra di figli di due genitori
     * presi a caso. Nessuna selezione: qui interessa sapere COSA la mutazione produce, non quale
     * forma vince. La selezione la fa la simulazione.
     */
    private static List<Sample> runLineage(GaitEvaluator ev, GenomeSchema schema, Random bodyRng,
                                           Random gaitRng, int generations, int trials, double seconds) {
        List<Genome> pop = new ArrayList<>();
        for (int i = 0; i < POPULATION; i++) {
            pop.add(Genome.random(schema, bodyRng, "f" + i, 0));
        }
        List<Sample> out = new ArrayList<>();
        out.add(measure(ev, schema, pop.get(0), 0, gaitRng, trials, seconds));

        for (int g = 1; g <= generations; g++) {
            List<Genome> next = new ArrayList<>();
            for (int i = 0; i < POPULATION; i++) {
                Genome a = pop.get(bodyRng.nextInt(pop.size()));
                Genome b = pop.get(bodyRng.nextInt(pop.size()));
                next.add(Genome.reproduce(a, b, schema, bodyRng, "g" + g + "-" + i, g));
            }
            pop = next;
            // si misura un individuo per generazione: la fisica costa, e qui conta la deriva
            // della forma lungo la stirpe, non la varianza interna a una generazione
            out.add(measure(ev, schema, pop.get(0), g, gaitRng, trials, seconds));
        }
        return out;
    }

    private static Sample measure(GaitEvaluator ev, GenomeSchema schema, Genome g, int generation,
                                  Random rng, int trials, double seconds) {
        BodyPlan plan = g.bodyPlan(schema);
        Automaton body = Automaton.fromPlan(plan, 1.0);
        GaitEvaluator.Result r = ev.optimize(body, Gait.seed(body, rng), trials, seconds, rng);
        return new Sample(generation, plan.size(), body.dofCount(), BodyTraits.of(body), r);
    }

    // ------------------------------------------------------------------ resoconto

    private static void report(List<Sample> samples, int generations, double elapsed) {
        System.out.println("Per generazione (mediana sulle stirpi):");
        System.out.println("  gen  segmenti   gdl   massa  appoggi  gamba   avanti   cadono");
        for (int g = 0; g <= generations; g++) {
            final int gen = g;
            List<Sample> atGen = samples.stream().filter(s -> s.generation() == gen).toList();
            if (atGen.isEmpty()) {
                continue;
            }
            System.out.printf(Locale.ROOT, "  %3d  %8.1f  %4.1f  %6.1f  %7.1f  %5.2f  %+7.3f  %3d/%d%n",
                    g,
                    median(atGen, s -> (double) s.segments()),
                    median(atGen, s -> (double) s.dofs()),
                    median(atGen, s -> s.traits().mass()),
                    median(atGen, s -> (double) s.traits().supportCount()),
                    median(atGen, s -> s.traits().legLength()),
                    median(atGen, s -> s.result().forwardSpeed()),
                    (int) atGen.stream().filter(s -> s.result().fell()).count(), atGen.size());
        }

        System.out.println();
        System.out.println("Per forma:");
        Map<String, List<Sample>> byShape = new LinkedHashMap<>();
        for (Sample s : samples) {
            byShape.computeIfAbsent(shapeOf(s), k -> new ArrayList<>()).add(s);
        }
        byShape.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    List<Sample> group = e.getValue();
                    System.out.printf(Locale.ROOT,
                            "  %-22s %3d corpi (%4.1f%%), avanti mediana %+.3f m/s, massima %+.3f, "
                                    + "cadono %d (col vecchio criterio %d)%n",
                            e.getKey(), group.size(), 100.0 * group.size() / samples.size(),
                            median(group, s -> s.result().forwardSpeed()),
                            group.stream().mapToDouble(s -> s.result().forwardSpeed()).max().orElse(0),
                            (int) group.stream().filter(s -> s.result().fell()).count(),
                            (int) group.stream().filter(s -> s.result().fellAtEnd()).count());
                });

        long changed = samples.stream().filter(s -> s.segments() != FOUNDER_SEGMENTS).count();
        System.out.println();
        System.out.printf(Locale.ROOT,
                "Corpi con un numero di segmenti diverso dal fondatore (%d): %d su %d (%.1f%%)%n",
                FOUNDER_SEGMENTS, changed, samples.size(), 100.0 * changed / samples.size());
        System.out.printf(Locale.ROOT, "Segmenti: da %d a %d. Gradi di liberta': da %d a %d.%n",
                samples.stream().mapToInt(Sample::segments).min().orElse(0),
                samples.stream().mapToInt(Sample::segments).max().orElse(0),
                samples.stream().mapToInt(Sample::dofs).min().orElse(0),
                samples.stream().mapToInt(Sample::dofs).max().orElse(0));
        System.out.printf(Locale.ROOT,
                "TOTALE: avanti mediana %+.3f m/s, cadono %d su %d (col vecchio criterio %d).%n",
                median(samples, s -> s.result().forwardSpeed()),
                (int) samples.stream().filter(s -> s.result().fell()).count(), samples.size(),
                (int) samples.stream().filter(s -> s.result().fellAtEnd()).count());
        System.out.printf(Locale.ROOT, "Tempo totale %.1f s per %d corpi misurati.%n", elapsed, samples.size());
    }

    /**
     * Come si tiene su questo corpo. Un solo appoggio non basta a descriverlo: chi si regge sul
     * tronco striscia e avrebbe bisogno di un'onda lungo il corpo, chi si regge su un arto fa
     * comunque una falcata.
     */
    private static String shapeOf(Sample s) {
        BodyTraits t = s.traits();
        if (t.crawls()) {
            return "striscia (tronco)";
        }
        return switch (t.supportCount()) {
            case 0 -> "nessun appoggio";
            case 1 -> "1 arto di appoggio";
            case 2 -> "bipede";
            case 3 -> "3 appoggi";
            case 4 -> "quadrupede";
            default -> t.supportCount() + " appoggi";
        };
    }

    private static double median(List<Sample> list, java.util.function.ToDoubleFunction<Sample> f) {
        double[] v = list.stream().mapToDouble(f).sorted().toArray();
        return v.length == 0 ? 0 : v[v.length / 2];
    }
}
