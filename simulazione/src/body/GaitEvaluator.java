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
    /**
     * Quanto vale lo spostamento di un corpo ABBASSATO rispetto a uno eretto.
     *
     * ERA 0.35, cioe' trascinarsi valeva un terzo di camminare. Ed era un errore della stessa
     * famiglia della vecchia classe Balance, solo nascosto nel PUNTEGGIO invece che nel
     * controllo: non diceva al corpo come muoversi, gli diceva che una morfologia che si
     * trascina non ha diritto di funzionare. Un corpo con UN SOLO ARTO DI APPOGGIO non puo'
     * stare eretto - si sposta tirandosi con l'arto che ha - e con 0.35 piu' il premio di
     * eretto il suo punteggio migliore restava sotto quello di un bipede qualunque: nelle misure
     * del 24/10/2026 gli 8 corpi a un solo appoggio risultavano "caduti" 8 volte su 8, in TUTTI
     * i bracci. Non stavano fallendo: erano contati come falliti per definizione.
     * A 1.0 il punteggio guarda solo QUANTO SI AVANZA, e come lo si fa e' affare del corpo.
     */
    public static volatile double CRAWL_WEIGHT = 1.0;
    /**
     * Sotto questa frazione dell'altezza iniziale il corpo si e' ABBASSATO. Si guarda il MINIMO
     * lungo tutta la prova, non il valore finale: altrimenti un corpo che si tuffa in avanti e
     * rotola fino a rialzarsi conta come uno che ha camminato.
     *
     * ATTENZIONE A COSA VUOL DIRE: e' una DESCRIZIONE della posa, non un giudizio. Chi striscia
     * e chi si trascina sta sotto questa soglia per tutta la vita e sta funzionando benissimo.
     * Finche' questo numero entrava nel punteggio (vedi CRAWL_WEIGHT e UPRIGHT_BONUS) decideva
     * quali morfologie potessero esistere; adesso serve solo a raccontare come si e' mosso un
     * corpo.
     */
    public static final double FALL_RATIO = 0.6;
    /**
     * Premio per essere rimasto eretto (m/s equivalenti a corpo perfettamente in piedi).
     *
     * ERA 0.25, e per un corpo lento era piu' del suo stesso avanzare: valeva piu' alzarsi che
     * muoversi. E' l'altra meta' dello stesso errore di {@link #CRAWL_WEIGHT}: stare in piedi
     * non e' uno scopo, e' una STRATEGIA - buona per un bipede, inutile per chi striscia. A 0 lo
     * scopo torna a essere uno solo, avanzare, che e' quello che serve per arrivare al cibo.
     */
    public static volatile double UPRIGHT_BONUS = 0.0;
    /**
     * Oltre questa velocita' (m/s) non si tratta di un'andatura ma di un corpo esploso: un giunto
     * instabile accumula energia e sparata via la ragdoll a decine di m/s. Misurato: una corsa su
     * ~330 arrivava a 20.9 m/s. Va riconosciuto nel PUNTEGGIO, non solo nel risultato finale,
     * perche' altrimenti la salita della collina andrebbe dritta a cercarsele: un'esplosione
     * sembra l'andatura piu' veloce di tutte.
     */
    public static final double MAX_PLAUSIBLE_SPEED = 5.0;

    /**
     * DI QUANTO SI CHIEDE DI GIRARE in una prova di svolta (rad): un quarto di giro.
     *
     * Perche' un BERSAGLIO e non "il massimo che riesci a fare". E' la differenza fra girare e
     * ruzzolare, e l'ho imparata sbagliando: col punteggio sull'imbardata massima la ricerca
     * trovava corpi che giravano a 10 rad/s - 95 giri al minuto - cadendo tutti, 6 su 6. Non era
     * un difetto della fisica ne' dei corpi: il punteggio chiedeva di ruotare il piu' possibile,
     * e il modo piu' rapido di ruotare e' perdere l'equilibrio e trottolare. Un'entita' che
     * trottola non arriva a una mela: le serve cambiare direzione DI QUANTO VUOLE e poi
     * fermarsi.
     *
     * E' un COMPITO, non una strategia: il mondo chiede di arrivare a una direzione, e come ci si
     * arriva resta affare del corpo. E' la stessa cosa che "avanzare" e' per il passo. Quello che
     * NON si fa e' premiare una posa: chi arriva alla direzione giusta strisciando ha ragione
     * quanto chi ci arriva in piedi.
     */
    public static volatile double TURN_TARGET = Math.PI / 2;
    /**
     * Tempo massimo concesso per arrivare al bersaglio (s): sotto ~0.8 rad/s non si arriva.
     * Era 3.0 nella prima misura (soglia 0.5 rad/s); portato a 2.0 perche' una prova di svolta
     * sono DUE corse di fisica e il costo si paga su ogni corpo che nasce.
     */
    public static volatile double TURN_SECONDS = 2.0;
    /**
     * Tempo che si guarda DOPO aver mollato lo sterzo (s).
     *
     * E' la meta' del compito che conta davvero: saper SMETTERE di girare. Senza questa finestra
     * una trottola arriverebbe al bersaglio col primo giro e prenderebbe punteggio pieno; con
     * questa, quello che si misura e' dove il corpo si trova QUANDO HA FINITO di girare.
     */
    public static volatile double TURN_RELEASE = 0.75;

    /**
     * Esito di una prova di SVOLTA: quanto bene questo corpo arriva a una direzione data.
     *
     * @param errore       di quanto ha sbagliato la direzione finale (rad, sempre >= 0)
     * @param girato       quanto ha girato in tutto (rad, segno = verso)
     * @param tempo        secondi impiegati ad arrivare al bersaglio (NaN = non ci e' arrivato)
     * @param erroreOpposto lo stesso errore chiedendo di girare dall'ALTRA parte: serve a
     *                      MISURARE l'assunzione dello specchio invece di darla per buona (vedi
     *                      il commento sulla svolta in Gait). NaN = non misurato.
     * @param avanti       quanto si e' spostato mentre girava (m/s): si riporta, non si giudica -
     *                      girare fermi e girare camminando sono due andature diverse e quale
     *                      serva lo dice il mondo, non questo punteggio
     * @param errorePartenza l'errore dello STESSO corpo CON LO STERZO A ZERO, cioe' senza nessuna
     *                      svolta. E' il braccio di controllo, e viaggia dentro il risultato
     *                      perche' senza di lui "errore 0.8" non vuol dire niente: non si saprebbe
     *                      se la ricerca ha trovato qualcosa o se quel corpo girava gia' da solo.
     */
    public record Turn(Gait gait, double errore, double girato, double tempo,
                       double erroreOpposto, double avanti, boolean fell,
                       double errorePartenza) {

        /** Quanto la svolta trovata guadagna sul non averne nessuna (rad di errore in meno). */
        public double guadagno() {
            return Double.isFinite(errorePartenza) ? errorePartenza - errore : Double.NaN;
        }

        /** Quanto vale questa svolta: quanto POCO si sbaglia la direzione finale. */
        public double score() {
            return Double.isFinite(errore) ? -errore : -1e9;
        }

        /** Vero se il bersaglio e' stato raggiunto entro il tempo concesso. */
        public boolean arrivato() {
            return Double.isFinite(tempo);
        }

        /** Vero se la svolta funziona anche dall'altra parte quasi come da questa. */
        public boolean simmetrica() {
            return Double.isFinite(erroreOpposto) && erroreOpposto <= errore + 0.3;
        }
    }

    /** Esito di una prova. */
    public record Result(Gait gait, double speed, double forwardSpeed, double drift,
                         boolean fell, double uprightRatio, double endRatio) {

        /**
         * Quanto vale questa andatura: quanto avanza NELLA DIREZIONE IN CUI GUARDA (+Z), non
         * quanto si sposta in generale. Serve che le due cose coincidano, altrimenti nel mondo
         * l'entita' andrebbe in una direzione mentre l'animazione la mostra sbilenca di lato.
         * La deriva laterale e' una penalita' leggera, e serve a tenere insieme il mondo e il
         * corpo: la simulazione muove l'entita' nella direzione in cui guarda, quindi un corpo
         * che avanza di traverso andrebbe in una direzione e sarebbe disegnato in un'altra.
         *
         * NON C'E' PIU' NESSUN PREMIO ALLA POSA. CRAWL_WEIGHT vale 1 e UPRIGHT_BONUS vale 0: il
         * punteggio e' quanto si avanza, e basta. Chi si trascina e arriva al cibo ha ragione
         * quanto chi ci cammina. Le due manopole restano per poter rifare la misura di prima.
         */
        public double score() {
            if (exploded()) {
                return -1e9;      // peggio di stare fermi: non deve mai essere scelta
            }
            double useful = forwardSpeed - 0.3 * drift;
            return useful * (fell ? CRAWL_WEIGHT : 1.0) + UPRIGHT_BONUS * uprightRatio;
            // con i valori predefiniti (1.0 e 0.0) questa riga e' esattamente "useful"
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
     * Una corsa di fisica in cui si chiede al corpo di GIRARE DI UN QUARTO DI GIRO e poi di
     * smettere: di quanto sbaglia la direzione finale.
     *
     * COME SI MISURA LA DIREZIONE. Non con JoltRagdoll.yawRoot: quello ricade sull'asse laterale
     * quando l'asse in avanti e' quasi verticale, e la ricaduta e' un SALTO - un corpo che
     * ruzzola ci passa dentro continuamente e sommando i salti risultava girare a 10 rad/s senza
     * aver cambiato direzione. Qui si integra la velocita' angolare del tronco ATTORNO ALLA
     * VERTICALE (rootAngularVelocity().y()): e' continua, non ha ricadute, e un corpo che rotola
     * attorno a un asse orizzontale non accumula niente - perche' rotolare non e' girare.
     *
     * DUE FASI: si tiene lo sterzo finche' il bersaglio non e' passato, poi si MOLLA e si guarda
     * dove il corpo si ferma. La seconda fase e' la meta' che conta: saper smettere.
     */
    public Turn measureTurn(Automaton body, Gait gait, double verso, double seconds) {
        JoltRagdoll doll = JoltRagdoll.spawn(world, body, 0, 0, 0, 0, nextGroup++);
        try {
            float[] q = null;
            int settleSteps = (int) Math.round(SETTLE_SECONDS / PhysicsWorld.TIME_STEP);
            int steps = (int) Math.round(seconds / PhysicsWorld.TIME_STEP);
            int rilascio = (int) Math.round(TURN_RELEASE / PhysicsWorld.TIME_STEP);
            MotorSense sensi = new MotorSense(body);
            q = gait.anglesAt(body, 0, q);
            doll.setTargetAngles(q);
            for (int i = 0; i < settleSteps; i++) {
                world.step();
            }
            double startHeight = doll.rootPosition().y();
            double bersaglio = verso * TURN_TARGET;
            double girato = 0;
            double tempo = Double.NaN;
            double lowestRatio = Double.MAX_VALUE;
            V3 comStart = doll.centerOfMass();
            int ogniQuanti = Math.max(1, CONTROL_EVERY);
            int totale = steps + rilascio;
            int i = 0;
            for (; i < totale; i++) {
                double t = i * PhysicsWorld.TIME_STEP;
                boolean arrivato = Double.isFinite(tempo);
                // oltre il tempo concesso si molla comunque: chi non ci arriva ha finito
                double sterzo = (arrivato || i >= steps) ? 0.0 : verso;
                if (i % ogniQuanti == 0) {
                    sensi.read(doll, 0, 2 * Math.PI * gait.frequency() * t);
                }
                q = gait.targetAngles(body, t, sensi, sterzo, q);
                doll.setTargetAngles(q);
                world.step();
                girato += doll.rootAngularVelocity().y() * PhysicsWorld.TIME_STEP;
                if (!arrivato && girato * verso >= TURN_TARGET) {
                    tempo = t;
                }
                double r = startHeight > 1e-6 ? doll.rootPosition().y() / startHeight : 0;
                lowestRatio = Math.min(lowestRatio, r);
                // chi e' arrivato ha finito la sua finestra di rilascio: non serve il resto
                if (arrivato && t >= tempo + TURN_RELEASE) {
                    break;
                }
            }
            double durata = Math.max(1e-3, i * PhysicsWorld.TIME_STEP);
            V3 comEnd = doll.centerOfMass();
            double avanti = Math.hypot(comEnd.x() - comStart.x(), comEnd.z() - comStart.z()) / durata;
            double errore = Math.abs(girato - bersaglio);
            return new Turn(gait, errore, girato, tempo, Double.NaN, avanti,
                    lowestRatio < FALL_RATIO, Double.NaN);
        } finally {
            doll.remove();
        }
    }

    /**
     * Cerca la SVOLTA di questo corpo: salita della collina sul solo blocco dello sterzo, col
     * punteggio sull'errore di direzione (vedi {@link Turn}).
     *
     * SI PARTE DA ZERO, cioe' da un corpo che non sa girare, e si misura anche quello: cosi' il
     * guadagno e' sempre leggibile contro "niente svolta" senza un braccio di controllo a parte.
     * Alla fine si fissa la convenzione (sterzo +1 = gira in positivo per tutti i corpi, perche'
     * l'entita' deve poter chiedere "a destra" e ottenere destra) e si misura anche il verso
     * opposto, per sapere quanto l'assunzione dello specchio tiene su questo corpo.
     *
     * Il passo dell'andatura NON si tocca: la svolta si cerca SOPRA l'andatura che il corpo ha
     * gia' trovato per camminare. Due ricerche separate con due punteggi separati, perche' una
     * prova di fisica risponde a una domanda sola.
     */
    public Turn optimizeTurn(Automaton body, Gait start, int trials, Random rng) {
        Gait best = start;
        // anche il punto di partenza si misura nei due versi: il confronto deve essere alla pari
        Turn p0 = measureTurn(body, best, 1.0, TURN_SECONDS);
        Turn p1 = measureTurn(body, best, -1.0, TURN_SECONDS);
        // l'errore del punto di partenza E' il braccio di controllo: lo sterzo qui e' tutto a zero
        double partenza = Math.max(p0.errore(), p1.errore());
        Turn bestResult = new Turn(best, p0.errore(), p0.girato(), p0.tempo(), p1.errore(),
                p0.avanti(), p0.fell() || p1.fell(), partenza);
        for (int i = 0; i < trials; i++) {
            double sigma = TURN_SIGMA * (1.0 - 0.7 * i / (double) Math.max(1, trials));
            Gait candidate = best.perturbSterzo(body, rng, sigma, TURN_FRACTION);
            /*
             * SI PROVANO I DUE VERSI E SI TIENE IL PEGGIORE DEI DUE. Non e' prudenza: una svolta
             * che funziona solo da un lato lascia l'entita' incapace di tornare indietro, e il
             * punteggio deve dire la verita' su cosa quel corpo sa fare. Costa due corse per
             * prova, ed e' il prezzo per non scoprire dopo che meta' delle svolte era finta.
             */
            Turn destra = measureTurn(body, candidate, 1.0, TURN_SECONDS);
            Turn sinistra = measureTurn(body, candidate, -1.0, TURN_SECONDS);
            double punteggio = Math.min(destra.score(), sinistra.score());
            if (punteggio > peggiore(bestResult)) {
                best = candidate;
                bestResult = new Turn(candidate, destra.errore(), destra.girato(), destra.tempo(),
                        sinistra.errore(), destra.avanti(), destra.fell() || sinistra.fell(),
                        partenza);
            }
        }
        if (bestResult.girato() < 0) {
            // convenzione: sterzo +1 gira in positivo per tutti (vedi Gait.sterzoRovesciato)
            best = best.sterzoRovesciato();
            Turn destra = measureTurn(body, best, 1.0, TURN_SECONDS);
            Turn sinistra = measureTurn(body, best, -1.0, TURN_SECONDS);
            return new Turn(best, destra.errore(), destra.girato(), destra.tempo(),
                    sinistra.errore(), destra.avanti(), destra.fell() || sinistra.fell(),
                    partenza);
        }
        return bestResult;
    }

    /** Il punteggio di una svolta tenendo conto anche del verso opposto, quando si conosce. */
    private static double peggiore(Turn t) {
        if (!Double.isFinite(t.erroreOpposto())) {
            return t.score();
        }
        return Math.min(t.score(), -t.erroreOpposto());
    }

    /** Quanto si sposta uno spostamento di centro a ogni prova di svolta (frazione dell'escursione). */
    public static volatile double TURN_SIGMA = 0.35;
    /** Quanti giunti si spostano a ogni prova di svolta. */
    public static volatile double TURN_FRACTION = 0.3;

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
