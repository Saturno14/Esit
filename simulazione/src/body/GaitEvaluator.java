package src.body;

import src.genetics.BodyPlan;
import src.genetics.GenomeSchema;

import java.util.Locale;
import java.util.Random;

/**
 * Misura con la FISICA VERA quanto si sposta un corpo con una data andatura, e cerca l'andatura
 * migliore per quel corpo. E' l'"oracolo" con cui la morfologia entra nell'evoluzione: due corpi
 * diversi, con le stesse regole, ottengono velocita' diverse perche' le gambe e la massa sono
 * diverse.
 *
 * Come si misura (e perche' cosi'):
 * - il corpo viene lasciato assestare qualche decimo di secondo, poi si accendono gli oscillatori;
 * - si guarda lo spostamento del baricentro nella SECONDA META' della corsa: cosi' un corpo che
 *   cade in avanti e poi resta fermo non guadagna velocita' dal tonfo iniziale;
 * - "velocita'" e' lo spostamento orizzontale per secondo, in qualunque direzione: strisciare
 *   vale come camminare, l'importante e' avanzare. La direzione in avanti (+Z) e la caduta
 *   vengono riportate a parte, perche' servono alla simulazione (chi e' caduto non e' in piedi).
 *
 * Un mondo fisico viene creato una volta e riusato per tutte le prove (crearlo costa piu' della
 * prova stessa). NON e' sicuro da usare da piu' thread: uno per thread.
 */
public final class GaitEvaluator implements AutoCloseable {

    /** Tempo lasciato al corpo per appoggiarsi a terra prima di cominciare a muoversi (s). */
    private static final double SETTLE_SECONDS = 0.3;
    /** Durata predefinita di una prova (s). */
    public static final double DEFAULT_SECONDS = 2.0;
    /** Prove dell'ottimizzazione: ogni prova e' una corsa di fisica. */
    public static final int DEFAULT_TRIALS = 24;
    /** Quanto vale lo spostamento di un corpo caduto rispetto a uno in piedi. */
    public static final double CRAWL_WEIGHT = 0.35;
    /**
     * Sotto questa frazione dell'altezza iniziale il corpo e' caduto. Si guarda il MINIMO lungo
     * tutta la prova, non il valore finale: altrimenti un corpo che si tuffa in avanti e rotola
     * fino a rialzarsi conta come uno che ha camminato.
     */
    public static final double FALL_RATIO = 0.6;
    /** Premio per essere rimasto in piedi (m/s equivalenti a corpo perfettamente eretto). */
    public static final double UPRIGHT_BONUS = 0.25;
    /**
     * Oltre questa velocita' (m/s) non si tratta di un'andatura ma di un corpo esploso: un giunto
     * instabile accumula energia e sparata via la ragdoll a decine di m/s. Misurato: una corsa su
     * ~330 arrivava a 20.9 m/s. Va riconosciuto nel PUNTEGGIO, non solo nel risultato finale,
     * perche' altrimenti la salita della collina andrebbe dritta a cercarsele: un'esplosione
     * sembra l'andatura piu' veloce di tutte.
     */
    public static final double MAX_PLAUSIBLE_SPEED = 5.0;

    /** Esito di una prova. */
    public record Result(Gait gait, double speed, double forwardSpeed, double drift,
                         boolean fell, double uprightRatio, double endRatio) {

        /**
         * Quanto vale questa andatura: quanto avanza NELLA DIREZIONE IN CUI GUARDA (+Z), non
         * quanto si sposta in generale. Serve che le due cose coincidano, altrimenti nel mondo
         * l'entita' andrebbe in una direzione mentre l'animazione la mostra sbilenca di lato.
         * La deriva laterale e' una penalita' leggera; chi e' caduto prende solo una frazione
         * (trascinarsi vale meno che camminare) e restare eretto da' un piccolo premio.
         */
        public double score() {
            if (exploded()) {
                return -1e9;      // peggio di stare fermi: non deve mai essere scelta
            }
            double useful = forwardSpeed - 0.3 * drift;
            return useful * (fell ? CRAWL_WEIGHT : 1.0) + UPRIGHT_BONUS * uprightRatio;
        }

        /**
         * Caduto secondo il criterio VECCHIO: si guarda solo come e' finito. Resta per poter
         * confrontare le misure di prima con quelle di adesso; {@link #fell()} e' il criterio
         * buono, perche' guarda il minimo lungo tutta la prova.
         */
        public boolean fellAtEnd() {
            return endRatio < FALL_RATIO;
        }

        /** True se la fisica e' andata in malora (valori non finiti o velocita' impossibili). */
        public boolean exploded() {
            return !Double.isFinite(speed) || !Double.isFinite(forwardSpeed) || !Double.isFinite(drift)
                    || !Double.isFinite(uprightRatio) || !Double.isFinite(endRatio)
                    || speed > MAX_PLAUSIBLE_SPEED;
        }
    }

    private final PhysicsWorld world;
    private int nextGroup = 1;

    public GaitEvaluator() {
        this.world = new PhysicsWorld(PhysicsWorld.MAX_PARTS_PER_BODY + 8, 0);
        this.world.addGround(60f, 0f, 0.95f);
    }

    /** Una corsa di fisica: quanto si sposta questo corpo con questa andatura. */
    public Result measure(Automaton body, Gait gait, double seconds) {
        JoltRagdoll doll = JoltRagdoll.spawn(world, body, 0, 0, 0, 0, nextGroup++);
        try {
            float[] q = null;
            int settleSteps = (int) Math.round(SETTLE_SECONDS / PhysicsWorld.TIME_STEP);
            int steps = (int) Math.round(seconds / PhysicsWorld.TIME_STEP);
            int half = steps / 2;

            /*
              * I SENSI DEL CORPO, non un controllore. Qui prima si costruiva un Balance - un
              * riflesso scritto a mano, con guadagni uguali per ogni morfologia. Adesso si
              * costruiscono solo gli INGRESSI (MotorSense) e ad usarli ci pensa il riflesso che
              * il corpo si porta dietro nell'andatura, con i pesi che ha ereditato e allenato.
              */
            MotorSense sensi = new MotorSense(body);
            q = gait.anglesAt(body, 0, q);
            doll.setTargetAngles(q);
            for (int i = 0; i < settleSteps; i++) {
                world.step();
            }
            double startHeight = doll.rootPosition().y();

            V3 comHalf = null;
            V3 comStart = doll.centerOfMass();
            double heightSum = 0;
            double lowestRatio = Double.MAX_VALUE;
            int ogniQuanti = Math.max(1, CONTROL_EVERY);
            for (int i = 0; i < steps; i++) {
                double t = i * PhysicsWorld.TIME_STEP;
                /*
                 * I SENSI SI RILEGGONO OGNI ogniQuanti PASSI, non a ogni passo. Leggere la
                 * propriocezione di tutti i giunti costa quanto un pezzo del passo di fisica, e
                 * un riflesso vero non gira a 120 Hz; a 40 Hz c'e' tutto quello che serve per
                 * reagire a una caduta, che dura decimi di secondo.
                 */
                if (i % ogniQuanti == 0) {
                    sensi.read(doll, 0, 2 * Math.PI * gait.frequency() * t);
                }
                q = gait.targetAngles(body, t, sensi, q);
                doll.setTargetAngles(q);
                world.step();
                // l'equilibrio si guarda a ogni passo: un corpo che si tuffa e poi rimbalza in
                // piedi non deve passare per uno che ha camminato
                double r = startHeight > 1e-6 ? doll.rootPosition().y() / startHeight : 0;
                heightSum += r;
                lowestRatio = Math.min(lowestRatio, r);
                if (i == half) {
                    comHalf = doll.centerOfMass();
                }
            }
            V3 comEnd = doll.centerOfMass();
            if (comHalf == null) {
                comHalf = comStart;
            }

            double window = Math.max(1e-3, (steps - half) * PhysicsWorld.TIME_STEP);
            double dx = comEnd.x() - comHalf.x();
            double dz = comEnd.z() - comHalf.z();
            double speed = Math.sqrt(dx * dx + dz * dz) / window;
            double forward = dz / window;
            double drift = Math.abs(dx) / window;

            double ratio = steps > 0 ? heightSum / steps : 0;
            double endRatio = startHeight > 1e-6 ? doll.rootPosition().y() / startHeight : 0;
            boolean fell = lowestRatio < FALL_RATIO;
            return new Result(gait, speed, forward, drift, fell, ratio, endRatio);
        } finally {
            doll.remove();
        }
    }

    public Result measure(Automaton body, Gait gait) {
        return measure(body, gait, DEFAULT_SECONDS);
    }

    /**
     * Cerca l'andatura migliore per questo corpo partendo da quella data (quella dei genitori, o
     * il seme strutturato): salita della collina a passi casuali sempre piu' piccoli. Poche prove
     * bastano, perche' il punto di partenza e' gia' sensato; con molte prove il corpo impara
     * andature decisamente migliori.
     */
    public Result optimize(Automaton body, Gait start, int trials, double seconds, Random rng) {
        Gait best = start != null ? start.adaptTo(body, rng) : Gait.seed(body, rng);
        Result bestResult = measure(body, best, seconds);

        // prima mossa, non casuale: provare l'onda al contrario. Decide se il corpo va avanti o
        // indietro, e nessuna perturbazione casuale potrebbe trovarlo (vedi Gait.reversed)
        Gait flipped = best.reversed();
        Result flippedResult = measure(body, flipped, seconds);
        if (flippedResult.score() > bestResult.score()) {
            best = flipped;
            bestResult = flippedResult;
        }

        for (int i = 0; i < trials; i++) {
            double sigma = 0.35 * (1.0 - 0.7 * i / (double) Math.max(1, trials));
            Gait candidate = best.perturb(body, rng, sigma, PERTURB_FRACTION);
            Result r = measure(body, candidate, seconds);
            if (r.score() > bestResult.score()) {
                best = candidate;
                bestResult = r;
            }
        }
        return bestResult;
    }

    /**
      * Ogni quanti passi di fisica il riflesso rilegge i sensi e ricalcola gli angoli. A 120 Hz
      * di fisica, 3 vuol dire un passo di controllo a 40 Hz.
      */
    public static volatile int CONTROL_EVERY = 3;

    /**
     * Quanti giunti si spostano a ogni prova. Con 75 parametri e 4 prove, muoverli tutti insieme
     * e' rumore: si cambia poco per volta (vedi Gait.perturb con la frazione).
     */
    public static volatile double PERTURB_FRACTION = 0.3;

    @Override
    public void close() {
        world.close();
    }

    // ------------------------------------------------------------------ prova a mano

    /**
     * Esperimento CONTROLLATO: lo stesso umanoide con una cosa cambiata alla volta (gambe corte,
     * gambe lunghe, corpo massiccio). Serve a isolare l'effetto di una misura sola sulla
     * velocita', ed e' per questo che i corpi sono costruiti a mano.
     *
     * NON dice cosa produce l'evoluzione: i corpi della simulazione nascono da una catena di
     * incroci e mutazioni del piano, con segmenti e gradi di liberta' che cambiano. Per quello
     * c'e' src.body.EvolvedBodies, che misura corpi davvero evoluti. Qui non ci sono piu' forme
     * "esotiche" inventate (c'era un serpente a 14 segmenti scritto a mano): una forma che
     * l'evoluzione non puo' produrre cosi' non dice nulla sul controllore che serve.
     *
     *   java -cp "classpath" src.body.GaitEvaluator [prove=24] [secondi=2]
     */
    public static void main(String[] args) {
        int trials = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_TRIALS;
        double seconds = args.length > 1 ? Double.parseDouble(args[1]) : DEFAULT_SECONDS;
        PhysicsNatives.require();
        GenomeSchema schema = GenomeSchema.loadFromClasspath("/gene_schema.json");

        record Case(String name, BodyPlan plan) {
        }
        java.util.List<Case> cases = new java.util.ArrayList<>();
        cases.add(new Case("umanoide medio", BodyPlan.humanoid(0.8, 0.15, 0.25, 1.00, 0.08, 0.15, 0.08, 0.20)));
        cases.add(new Case("gambe corte", BodyPlan.humanoid(0.8, 0.15, 0.25, 0.70, 0.08, 0.15, 0.08, 0.20)));
        cases.add(new Case("gambe lunghe", BodyPlan.humanoid(0.8, 0.15, 0.25, 1.40, 0.08, 0.15, 0.08, 0.20)));
        cases.add(new Case("tozzo e massiccio", BodyPlan.humanoid(1.2, 0.15, 0.25, 0.70, 0.15, 0.15, 0.08, 0.20)));

        long t0 = System.nanoTime();
        try (GaitEvaluator ev = new GaitEvaluator()) {
            for (Case c : cases) {
                Automaton body = Automaton.fromPlan(c.plan(), 1.0);
                BodyTraits traits = BodyTraits.of(body);
                Random rng = new Random(42);
                Result r = ev.optimize(body, Gait.seed(body, rng), trials, seconds, rng);
                System.out.printf(Locale.ROOT,
                        "%-20s %s%n                     velocita' %.3f m/s (avanti %+.3f, deriva %.3f), "
                                + "caduto=%s, frequenza %.2f Hz%n",
                        c.name(), traits, r.speed(), r.forwardSpeed(), r.drift(), r.fell() ? "si" : "no",
                        r.gait().frequency());
            }
        }
        System.out.printf(Locale.ROOT, "%nTempo totale %.1f s (%d prove per corpo da %.1f s)%n",
                (System.nanoTime() - t0) / 1e9, trials + 1, seconds);
        System.exit(0);
    }
}
