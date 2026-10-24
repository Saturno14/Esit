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
 *   java -cp "classpath" src.body.EvolvedBodies [generazioni=12] [stirpi=6] [prove=4] [secondi=1.5] [seme=1] [seme-andatura] [riflesso] [peso-oscillatore]
 */
public final class EvolvedBodies {

    /** Individui per generazione in una stirpe: serve solo a dare due genitori diversi. */
    private static final int POPULATION = 6;
    /** Segmenti del fondatore umanoide: serve a dire quanti corpi hanno cambiato struttura. */
    private static final int FOUNDER_SEGMENTS = 18;

    private EvolvedBodies() {
    }

    private static double prop(String nome, double seDefault) {
        String v = System.getProperty(nome);
        return v == null || v.isBlank() ? seDefault : Double.parseDouble(v);
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
        // "aperto" adesso vuol dire SENZA RIFLESSO: il solo oscillatore a ciclo aperto, che e'
        // il braccio di controllo del movimento appreso (vedi Reflex)
        Gait.useReflex = !(args.length > 6 && args[6].equalsIgnoreCase("aperto"));
        /*
         * Il settimo argomento e' il PESO DELL'OSCILLATORE, e serve a misurare il caso limite:
         * con 0 l'oscillatore resta solo un orologio (i canali orologio.sin/cos di MotorSense) e
         * tutto il movimento deve uscire dai pesi del riflesso. E' il braccio "totalmente
         * autonomo": nessuna forma d'onda data da fuori, nemmeno una sinusoide.
         */
        if (args.length > 7) {
            Gait.oscWeight = Double.parseDouble(args[7]);
        }
        /*
         * Le manopole del riflesso si passano come proprieta' di sistema, non come argomenti:
         * sono parametri di RICERCA, non di esperimento, e tenerle fuori dalla lista posizionale
         * evita che un confronto appaiato cambi significato perche' si e' aggiunto un argomento.
         */
        Gait.REFLEX_SIGMA = prop("riflesso.sigma", Gait.REFLEX_SIGMA);
        Gait.REFLEX_FRACTION = prop("riflesso.frazione", Gait.REFLEX_FRACTION);
        Gait.REFLEX_SHARE = prop("riflesso.quota", Gait.REFLEX_SHARE);
        Reflex.MAX_CORRECTION = prop("riflesso.tetto", Reflex.MAX_CORRECTION);

        PhysicsNatives.require();
        GenomeSchema schema = GenomeSchema.loadFromClasspath("/gene_schema.json");
        System.out.printf(Locale.ROOT,
                "Corpi evoluti: %d stirpi x %d generazioni, %d prove da %.1f s, seme dell'andatura %s%n"
                        + "mutazione del corpo %s%n%n",
                lineages, generations, trials, seconds,
                (Gait.useLegacySeed ? "VECCHIO" : "nuovo")
                        + (Gait.useReflex ? ", con riflesso appreso" : ", CICLO APERTO")
                        + (Gait.oscWeight == 1.0 ? "" : String.format(Locale.ROOT,
                                ", oscillatore x%.2f", Gait.oscWeight)),
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

        /*
         * SU COSA SI SONO BASATI, FORMA PER FORMA.
         *
         * E' la domanda vera di tutto il lavoro sul movimento appreso: non "cadono meno?" ma "i
         * rami divergono?". Se i bipedi finiscono a reggersi sul beccheggio e i quadrupedi sul
         * contatto dell'arto opposto, allora ogni morfologia ha trovato la SUA strategia e non
         * gliel'ha data nessuno. Se trovano tutti la stessa cosa, e' una scoperta altrettanto
         * interessante, ma di segno opposto.
         *
         * Si somma il valore assoluto dei pesi per canale su tutto il corpo (vedi
         * Reflex.pesoPerCanale) e si stampano i tre canali piu' carichi di ogni forma.
         */
        if (Gait.useReflex) {
            System.out.println();
            System.out.println("Su cosa si e' basato il riflesso, per forma (i tre canali piu' carichi):");
            byShape.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                double[] tot = new double[MotorSense.CHANNELS];
                int conRiflesso = 0;
                for (Sample s : e.getValue()) {
                    Reflex r = s.result().gait() == null ? null : s.result().gait().reflex();
                    if (r == null) {
                        continue;
                    }
                    conRiflesso++;
                    double[] p = r.pesoPerCanale();
                    for (int c = 0; c < tot.length; c++) {
                        tot[c] += p[c];
                    }
                }
                if (conRiflesso == 0) {
                    return;
                }
                Integer[] ordine = new Integer[MotorSense.CHANNELS];
                for (int c = 0; c < ordine.length; c++) {
                    ordine[c] = c;
                }
                java.util.Arrays.sort(ordine, (x, y) -> Double.compare(tot[y], tot[x]));
                StringBuilder sb = new StringBuilder();
                double somma = 0;
                for (double v : tot) {
                    somma += v;
                }
                for (int k = 0; k < 3; k++) {
                    int c = ordine[k];
                    sb.append(String.format(Locale.ROOT, "  %s %.0f%%",
                            MotorSense.NAMES[c], somma > 0 ? 100.0 * tot[c] / somma : 0));
                }
                System.out.printf(Locale.ROOT, "  %-22s %s%n", e.getKey(), sb);
            });
        }

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
        scriviCsv(samples);
    }

    /**
     * Una riga per corpo, se si passa -Dcsv=&lt;file&gt;. Serve al CONFRONTO APPAIATO.
     *
     * I corpi nascono da un generatore separato da quello dell'andatura (bodyRng), quindi due
     * bracci lanciati con lo stesso seme misurano GLI STESSI CORPI NELLO STESSO ORDINE: la riga n
     * di un file e' lo stesso corpo della riga n dell'altro. Serve perche' la differenza fra due
     * mediane, su settantotto corpi che vanno da otto a ventidue segmenti, e' dentro la varianza
     * fra i corpi: a dire se un braccio e' meglio puo' rispondere solo il confronto corpo per
     * corpo.
     */
    private static void scriviCsv(List<Sample> samples) {
        String csv = System.getProperty("csv");
        if (csv == null || csv.isBlank()) {
            return;
        }
        StringBuilder sb = new StringBuilder("i;gen;forma;segmenti;gdl;appoggi;avanti;cade\n");
        for (int i = 0; i < samples.size(); i++) {
            Sample s = samples.get(i);
            sb.append(String.format(Locale.ROOT, "%d;%d;%s;%d;%d;%d;%.4f;%d%n",
                    i, s.generation(), shapeOf(s), s.segments(), s.dofs(),
                    s.traits().supportCount(), s.result().forwardSpeed(),
                    s.result().fell() ? 1 : 0));
        }
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of(csv), sb.toString());
            System.out.println("Righe per corpo scritte in " + csv);
        } catch (java.io.IOException e) {
            System.out.println("Non ho potuto scrivere " + csv + ": " + e.getMessage());
        }
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
