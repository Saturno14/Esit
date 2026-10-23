package src.body;

import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dice a che velocita' puo' muoversi un corpo, misurandolo con la fisica vera.
 *
 * COME SI INCASTRA CON LA SIMULAZIONE. Far girare la fisica di tutte le entita' a ogni tick
 * costa troppo per girare a x50 (misurato: 380 corpi articolati = 1.9 ms per passo di fisica,
 * cioe' circa 116 ms per tick di mondo, contro i 10 ms disponibili a x50). Qui la fisica si usa
 * invece come ORACOLO: ogni corpo nuovo viene fatto camminare per un paio di secondi in Jolt,
 * una volta sola, e il risultato (velocita' reale in m/s, stabilita', andatura) resta in cache.
 * La misura avviene in thread a parte: il tick non aspetta mai. Finche' il risultato non c'e',
 * si usa una stima geometrica ({@link #estimate}), poi sostituita da quella misurata.
 *
 * Con la fisica non disponibile (nativi mancanti, altra piattaforma) tutto continua a funzionare
 * con la sola stima geometrica.
 */
public final class Locomotion {

    /**
     * Prove di ottimizzazione dell'andatura per una fondatrice (ognuna e' una corsa di fisica).
     *
     * Misurato su 78 corpi evoluti, con la fisica sistemata (vedi STATO_LAVORO.txt del
     * 23/10/2026): 4 prove danno una mediana di +0.045 m/s e 70 corpi su 78 che cadono, 12 prove
     * +0.308 e 67, 40 prove +0.758 e 42. Il controllore impara, se gli si da' il tempo: e' il
     * budget il collo di bottiglia, non l'architettura. 24 e' il compromesso con il costo (circa
     * mezzo secondo di calcolo per fondatrice).
     */
    public static volatile int trialsPerBody = 24;
    /**
     * Prove per chi eredita gia' un'andatura dai genitori. Non zero: misurata, una sola corsa per
     * figlio lascia la popolazione a una mediana di 0.03 m/s, cioe' praticamente nessuno impara a
     * camminare e tutte le differenze tra corpi finiscono schiacciate sul minimo. Con qualche
     * prova ogni generazione affina l'andatura che ha ricevuto: e' lo stesso meccanismo che il
     * progetto usa gia' per il cervello (salita della collina durante la vita, WINDOW_SIZE in
     * entity.java), applicato al modo di camminare.
     */
    public static volatile int trialsPerChild = 8;
    /** Durata di una corsa di fisica (s). */
    public static volatile double secondsPerTrial = 1.5;
    /** Thread dedicati alla misura: ognuno ha il suo mondo fisico. */
    public static volatile int workers = 4;
    /** Con false la fisica non viene usata: solo stima geometrica (per confronti A/B). */
    public static volatile boolean usePhysics = true;

    /**
     * Velocita' in avanti (m/s) del corpo "normale" della simulazione: 0.27 m/s, mediana misurata
     * sulle 1600 andature di tre corse da 6000 tick.
     *
     * Non e' il valore di un corpo ottimizzato a lungo (un umanoide medio con 150 prove arriva a
     * 0.54 m/s): quello che conta e' cosa ottiene un corpo con il budget di prove che la
     * simulazione si puo' permettere davvero, altrimenti meta' popolazione finisce sotto il
     * minimo di passo e le differenze tra morfologie si appiattiscono.
     *
     * PERCHE' SERVE. La griglia della simulazione non e' mai stata metrica: un'azione di
     * movimento valeva una cella (1 m) in mezzo secondo, cioe' 2 m/s, quattro volte piu' veloce
     * di un corpo umanoide vero. Usando i m/s crudi come lunghezza del passo le entita' si
     * muovono quattro-cinque volte piu' piano, non arrivano al cibo e la popolazione crolla
     * (misurato: 12 semi, da mediana 15 vivi a mediana 2, con un'estinzione). Il passo si misura
     * quindi in RAPPORTO a questo riferimento: il corpo medio fa un passo come prima, e quello
     * che conta e' quanto una morfologia e' piu' veloce o piu' lenta della media.
     */
    public static final double REFERENCE_SPEED = 0.27;

    /**
     * Quello che la simulazione usa per muovere un'entita'.
     *
     * @param speed      velocita' di avanzamento (m/s) nella direzione in cui guarda
     * @param stable     true se il corpo resta in piedi mentre si muove
     * @param gait       andatura con cui si ottiene questa velocita' (serve anche all'animazione)
     * @param measured   true se viene dalla fisica, false se e' la stima geometrica
     * @param score      punteggio della misura (vedi GaitEvaluator.Result.score): serve
     *                   all'allenamento durante la vita per sapere se una prova e' andata meglio.
     *                   Per la stima geometrica e' meno infinito: non e' confrontabile con niente.
     */
    public record Profile(double speed, boolean stable, Gait gait, boolean measured, double score) {

        Profile(double speed, boolean stable, Gait gait, boolean measured) {
            this(speed, stable, gait, measured, Double.NEGATIVE_INFINITY);
        }
    }

    private static final Map<String, Profile> cache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> pending = new ConcurrentHashMap<>();
    private static final LinkedBlockingQueue<Request> queue = new LinkedBlockingQueue<>();
    private static final AtomicLong measured = new AtomicLong();
    private static final AtomicLong requested = new AtomicLong();
    /** Andature migliori trovate allenandosi, in attesa che l'entita' le venga a prendere. */
    private static final Map<Long, Profile> learned = new ConcurrentHashMap<>();
    private static final Map<Long, Boolean> practising = new ConcurrentHashMap<>();
    private static final AtomicLong practices = new AtomicLong();
    private static final AtomicLong improvements = new AtomicLong();
    private static final AtomicLong practicesSkipped = new AtomicLong();
    private static volatile boolean started = false;

    /**
     * @param learner entita' che si sta allenando (0 = nessuna: misura normale)
     * @param from    profilo da cui parte l'allenamento; se non e' null questa e' una PROVA di
     *                allenamento, cioe' una sola corsa di fisica su un'andatura perturbata
     */
    private record Request(String key, Automaton body, Gait start, long seed, int trials,
            long learner, Profile from) {

        Request(String key, Automaton body, Gait start, long seed, int trials) {
            this(key, body, start, seed, trials, 0L, null);
        }

        boolean isPractice() {
            return from != null;
        }
    }

    private Locomotion() {
    }

    // ------------------------------------------------------------------ interfaccia

    /**
     * Profilo di locomozione di un corpo. Non blocca mai: se la misura non c'e' ancora,
     * restituisce la stima geometrica e mette il corpo in coda.
     *
     * @param start andatura di partenza (ereditata dai genitori), puo' essere null
     */
    public static Profile profile(Automaton body, Gait start, long seed) {
        return profile(body, start, seed, trialsPerBody);
    }

    /**
     * Come sopra, dicendo quante prove di ottimizzazione fare.
     *
     * In simulazione i FIGLI ereditano l'andatura dai genitori: basta misurarla una volta
     * (trials = 0, una sola corsa di fisica, circa 20 ms) e lasciare che sia la selezione a
     * migliorarla di generazione in generazione. L'ottimizzazione vera, piu' costosa, serve solo
     * alle FONDATRICI, che partono da un'andatura inventata a tavolino.
     */
    public static Profile profile(Automaton body, Gait start, long seed, int trials) {
        String key = signature(body, start);
        Profile cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (usePhysics && PhysicsNatives.tryLoad()) {
            enqueue(key, body, start, seed, trials);
        }
        return estimate(body, start);
    }

    /** Misura subito, sul thread del chiamante (per strumenti e test, non per il tick). */
    public static Profile profileNow(Automaton body, Gait start, long seed) {
        String key = signature(body, start);
        Profile cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (!usePhysics || !PhysicsNatives.tryLoad()) {
            return estimate(body, start);
        }
        try (GaitEvaluator ev = new GaitEvaluator()) {
            Profile p = run(ev, body, start, seed, trialsPerBody);
            cache.put(key, p);
            return p;
        }
    }

    /**
     * Stima geometrica, senza fisica: una falcata vale circa mezza gamba, la cadenza scende con
     * la massa sostenuta da ogni appoggio, e un corpo senza arti di appoggio striscia. Serve solo
     * come valore provvisorio e come ripiego: i numeri veri vengono dalla fisica.
     */
    public static Profile estimate(Automaton body, Gait gait) {
        BodyTraits t = BodyTraits.of(body);
        double stride = 0.5 * t.legLength();
        if (t.supportCount() == 0 || stride <= 0) {
            // corpo che striscia: avanza per ondulazione, tanto piu' quanto e' lungo
            double speed = Math.min(0.6, 0.08 * t.longestLimb());
            return new Profile(speed, true, gait, false);
        }
        // cadenza di un pendolo di lunghezza pari alla gamba, ridotta dal carico per appoggio
        double cadence = 1.0 / (2 * Math.PI) * Math.sqrt(Automaton.GRAVITY / Math.max(0.1, t.legLength()));
        double loadFactor = 1.0 / (1.0 + t.loadPerSupport() / 120.0);
        double speed = stride * cadence * loadFactor * Math.min(1.5, t.supportCount() / 2.0);
        return new Profile(Math.max(0.05, speed), t.supportCount() >= 2, gait, false);
    }

    /** Quante misure sono state fatte e quante chieste, e a che velocita' camminano (diagnostica). */
    public static String stats() {
        return "locomozione: " + measured.get() + " corpi misurati su " + requested.get()
                + " richiesti, " + cache.size() + " in cache, " + queue.size() + " in coda"
                + String.format(Locale.ROOT, ", velocita' misurate: mediana %.3f media %.3f max %.3f m/s su %d",
                        medianSpeed(), meanSpeed(), maxSpeed(), measuredCount());
    }

    /** Quanto ha reso l'allenamento durante la vita (diagnostica). */
    public static String practiceStats() {
        long tried = practices.get();
        return "allenamento: " + tried + " prove, " + improvements.get() + " miglioramenti"
                + (tried > 0 ? String.format(Locale.ROOT, " (%.0f%%)", 100.0 * improvements.get() / tried) : "")
                + ", " + practicesSkipped.get() + " saltate per coda piena";
    }

    /**
     * Mediana delle velocita' effettivamente misurate. Serve a tarare REFERENCE_SPEED: il valore
     * giusto non e' quello di un corpo ottimizzato a lungo, ma quello che un corpo ottiene con il
     * budget di prove che la simulazione puo' permettersi davvero.
     */
    public static double medianSpeed() {
        double[] v = measuredSpeeds();
        if (v.length == 0) {
            return 0;
        }
        java.util.Arrays.sort(v);
        return v[v.length / 2];
    }

    public static double meanSpeed() {
        double[] v = measuredSpeeds();
        double s = 0;
        for (double d : v) {
            s += d;
        }
        return v.length == 0 ? 0 : s / v.length;
    }

    public static double maxSpeed() {
        double m = 0;
        for (double d : measuredSpeeds()) {
            m = Math.max(m, d);
        }
        return m;
    }

    public static int measuredCount() {
        return measuredSpeeds().length;
    }

    private static double[] measuredSpeeds() {
        return cache.values().stream().filter(Profile::measured).mapToDouble(Profile::speed).toArray();
    }

    /**
     * Registra un profilo gia' noto sotto la coppia corpo+andatura data. Serve a chi adotta
     * l'andatura uscita dalla misura: la firma cambia, ma la misura e' la stessa e non va rifatta.
     */
    public static void remember(Automaton body, Gait gait, Profile p) {
        cache.putIfAbsent(signature(body, gait), p);
    }

    // ------------------------------------------------------------------ allenarsi vivendo

    /**
     * ALLENARSI A CAMMINARE DURANTE LA VITA.
     *
     * Chiede UNA prova: si perturba di poco l'andatura che l'entita' sta usando, la si misura
     * nella fisica, e se va meglio prende il posto della vecchia. E' la stessa salita della
     * collina che entity.java fa sul cervello dentro la finestra di valutazione, applicata al
     * modo di camminare, e si incastra con l'ereditarieta' che c'e' gia': i figli partono
     * dall'andatura incrociata dei genitori, quindi quello che un genitore ha imparato in vita
     * passa ai figli e si accumula di generazione in generazione (effetto Baldwin).
     *
     * PERCHE' NON TUTTO ALLA NASCITA. Prima l'addestramento era un blocco di prove pagate tutte
     * alla nascita (24 per una fondatrice, 8 per un figlio): il numero di prove era un parametro
     * fisso, slegato dalla vita dell'entita'. Spalmandolo, il costo si distribuisce nel tempo
     * invece di arrivare in un colpo, e chi vive piu' a lungo si allena di piu' - cioe' camminare
     * bene diventa anche una conseguenza dell'essere sopravvissuto, non solo un dono alla nascita.
     *
     * NON BLOCCA MAI. La prova va in coda ai thread di misura; il risultato si ritira dopo, con
     * {@link #collectPractice}. Se la coda e' gia' piena la prova si BUTTA invece di accodarla:
     * con qualche centinaio di entita' vive la coda crescerebbe senza fine e le misure dei corpi
     * nuovi - che servono subito, perche' senza di loro un'entita' non sa nemmeno quanto e' lunga
     * la sua falcata - resterebbero dietro all'allenamento di chi cammina gia'.
     *
     * @param learner identificativo dell'entita' (una prova per volta a testa)
     * @param from    profilo misurato da cui si parte: senza misura non c'e' niente da migliorare
     */
    public static void practice(long learner, Automaton body, Profile from, long seed) {
        if (!lifetimeLearning || from == null || !from.measured() || from.gait() == null) {
            return;
        }
        if (!usePhysics || !PhysicsNatives.tryLoad()) {
            return;
        }
        if (queue.size() >= maxPracticeQueue) {
            practicesSkipped.incrementAndGet();
            return;
        }
        if (practising.putIfAbsent(learner, Boolean.TRUE) != null) {
            return;     // sta gia' facendo una prova
        }
        practices.incrementAndGet();
        ensureWorkers();
        queue.offer(new Request(signature(body, from.gait()), body, from.gait(), seed, 1,
                learner, from));
    }

    /**
     * Ritira l'andatura migliore trovata da una prova di allenamento, o null se non ce n'e' una:
     * perche' la prova non e' ancora finita, o perche' e' andata peggio e non c'e' niente da
     * imparare. Si ritira una volta sola.
     */
    public static Profile collectPractice(long learner) {
        return learned.remove(learner);
    }

    /** Un'entita' morta non ha piu' niente da ritirare. */
    public static void forget(long learner) {
        learned.remove(learner);
        practising.remove(learner);
    }

    /** Con false si torna all'addestramento tutto alla nascita, per rifare il confronto. */
    public static volatile boolean lifetimeLearning = true;

    /**
     * Quanto si sposta l'andatura a ogni prova di allenamento. Piu' piccolo di quello
     * dell'ottimizzazione iniziale (0.35 che scende a 0.1): qui si parte da un'andatura che
     * funziona gia' e si cerca di rifinirla, non di esplorare.
     */
    public static volatile double practiceSigma = 0.15;

    /**
     * Oltre questa coda le prove di allenamento si buttano: hanno la priorita' piu' bassa di
     * tutto, perche' un'entita' che si allena cammina gia'.
     */
    public static volatile int maxPracticeQueue = 64;

    public static void clearCache() {
        cache.clear();
        pending.clear();
        queue.clear();
        learned.clear();
        practising.clear();
    }

    // ------------------------------------------------------------------ misura in background

    private static void enqueue(String key, Automaton body, Gait start, long seed, int trials) {
        if (pending.putIfAbsent(key, Boolean.TRUE) != null) {
            return;     // gia' in coda
        }
        requested.incrementAndGet();
        ensureWorkers();
        queue.offer(new Request(key, body, start, seed, trials));
    }

    private static synchronized void ensureWorkers() {
        if (started) {
            return;
        }
        started = true;
        int n = Math.max(1, workers);
        for (int i = 0; i < n; i++) {
            Thread t = new Thread(Locomotion::workerLoop, "esit-locomozione-" + i);
            t.setDaemon(true);
            t.start();
        }
    }

    private static void workerLoop() {
        GaitEvaluator ev = null;
        try {
            ev = new GaitEvaluator();
            while (true) {
                Request r = queue.take();
                if (r.isPractice()) {
                    try {
                        practiceStep(ev, r);
                    } catch (Throwable t) {
                        // una prova andata male non costa niente: resta l'andatura di prima
                    } finally {
                        practising.remove(r.learner());
                    }
                    continue;
                }
                try {
                    Profile p = run(ev, r.body(), r.start(), r.seed(), r.trials());
                    cache.put(r.key(), p);
                    measured.incrementAndGet();
                } catch (Throwable t) {
                    // un corpo che fa esplodere la fisica non deve fermare la simulazione:
                    // resta la stima geometrica
                    cache.put(r.key(), estimate(r.body(), r.start()));
                } finally {
                    pending.remove(r.key());
                }
            }
        } catch (InterruptedException ignored) {
            // chiusura
        } catch (Throwable t) {
            System.out.println("[locomozione] thread di misura fermato: " + t);
        } finally {
            if (ev != null) {
                ev.close();
            }
        }
    }

    /**
     * Una prova di allenamento: una perturbazione, una corsa di fisica, e si tiene solo se va
     * meglio. L'andatura migliorata va anche in cache sotto la sua firma, cosi' i figli che la
     * ereditano non la rimisurano da zero.
     */
    private static void practiceStep(GaitEvaluator ev, Request r) {
        Random rng = new Random(r.seed());
        Gait candidate = r.start().perturb(r.body(), rng, practiceSigma, GaitEvaluator.PERTURB_FRACTION);
        GaitEvaluator.Result res = ev.measure(r.body(), candidate, secondsPerTrial);
        if (res.exploded() || res.score() <= r.from().score()) {
            return;
        }
        Profile p = new Profile(Math.max(0.0, res.forwardSpeed()), !res.fell(), res.gait(), true,
                res.score());
        cache.putIfAbsent(signature(r.body(), res.gait()), p);
        learned.put(r.learner(), p);
        improvements.incrementAndGet();
    }

    private static Profile run(GaitEvaluator ev, Automaton body, Gait start, long seed, int trials) {
        Random rng = new Random(seed);
        GaitEvaluator.Result r = ev.optimize(body, start, Math.max(0, trials), secondsPerTrial, rng);
        if (r.exploded()) {
            // corpo che la fisica non regge: si tiene la stima geometrica invece di un numero falso
            return estimate(body, start);
        }
        // la velocita' utile e' quella in avanti: e' la direzione in cui l'entita' si muovera'
        double speed = Math.max(0.0, r.forwardSpeed());
        return new Profile(speed, !r.fell(), r.gait(), true, r.score());
    }

    /**
     * Firma del corpo + andatura: due corpi con la stessa firma hanno la stessa locomozione, e la
     * misura si riusa. I figli di genitori identici (cloni, fondatrici dello stesso piano)
     * ricadono qui e non pagano la fisica.
     */
    private static String signature(Automaton body, Gait gait) {
        StringBuilder sb = new StringBuilder(body.links().size() * 12);
        for (Link l : body.links()) {
            sb.append(l.name).append(':')
                    .append(round(l.length)).append(',').append(round(l.radius)).append(',')
                    .append(round(l.dir.x())).append(',').append(round(l.dir.y())).append(',').append(round(l.dir.z()))
                    .append(';');
        }
        sb.append("|dof=").append(body.dofCount());
        if (gait != null) {
            sb.append("|g=").append(round(gait.frequency()));
            for (Map.Entry<String, Gait.Osc> e : gait.oscillators().entrySet()) {
                sb.append(e.getKey()).append(round(e.getValue().amplitude()))
                        .append(round(e.getValue().phase())).append(round(e.getValue().center()));
            }
        }
        return Long.toHexString(hash(sb)) + "-" + body.links().size() + "-" + body.dofCount();
    }

    private static String round(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static long hash(CharSequence s) {
        long h = 1125899906842597L;
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        return h;
    }
}
