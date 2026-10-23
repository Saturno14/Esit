package src.body;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Controllore dell'andatura: un oscillatore per ogni grado di liberta'. L'angolo obiettivo del
 * giunto nel tempo e'
 *
 *   q(t) = centro + ampiezza * sin(2*pi*frequenza*t + fase)
 *
 * Poche cose per giunto (ampiezza, fase, centro) e una frequenza comune a tutto il corpo. E'
 * volutamente semplice: dentro la fisica vera (vedi {@link GaitEvaluator}) basta per far
 * avanzare un corpo, e ha pochi parametri da far evolvere insieme alla morfologia.
 *
 * I parametri sono indicizzati per NOME del grado di liberta' (es. "hip_L_x"), non per
 * posizione: cosi' un figlio che ha perso o guadagnato segmenti conserva l'andatura dei giunti
 * che ha ancora, e i giunti nuovi partono casuali. E' lo stesso criterio con cui BodyPlan
 * eredita i segmenti per id.
 *
 * Angoli in radianti, tempo in secondi.
 */
public final class Gait {

    /** Intervallo di frequenza dell'andatura (Hz). */
    public static final double FREQ_MIN = 0.3;
    public static final double FREQ_MAX = 3.0;

    /** Parametri di un singolo grado di liberta'. */
    public record Osc(double amplitude, double phase, double center) {
    }

    private final double frequency;
    private final Map<String, Osc> oscByDof;

    private Gait(double frequency, Map<String, Osc> oscByDof) {
        this.frequency = Math.max(FREQ_MIN, Math.min(FREQ_MAX, frequency));
        this.oscByDof = oscByDof;
    }

    public double frequency() {
        return frequency;
    }

    public Osc osc(String dofName) {
        return oscByDof.get(dofName);
    }

    public Map<String, Osc> oscillators() {
        return java.util.Collections.unmodifiableMap(oscByDof);
    }

    // ------------------------------------------------------------------ costruzione

    /** Andatura ferma: tutti i giunti alla posa di riposo (nessun movimento). */
    public static Gait rest(Automaton body) {
        Map<String, Osc> map = new LinkedHashMap<>();
        for (Dof d : body.dofs()) {
            map.put(d.name, new Osc(0, 0, 0));
        }
        return new Gait(1.0, map);
    }

    /** Ampiezza dell'oscillazione che fa il passo, sull'asse sagittale di un arto di appoggio (rad). */
    private static final double SWING_AMPLITUDE = 0.45;
    /** Ampiezza sugli altri assi dello stesso arto: serve a non sbilanciarsi di lato. */
    private static final double SUPPORT_SIDE_AMPLITUDE = 0.10;
    /** Ampiezza degli arti che non portano il peso (braccia, collo): accompagnano, non spingono. */
    private static final double FREE_AMPLITUDE = 0.12;
    /** Ritardo di fase per ogni segmento di distanza dall'attacco dell'arto (rad). */
    private static final double LAG_PER_SEGMENT = 0.7;
    /** Un asse conta come sagittale (quello che fa avanzare) se e' quasi parallelo a X. */
    private static final double SAGITTAL_DOT = 0.7;

    /**
     * Ipotesi di partenza con un po' di struttura, invece che tutta casuale. Quattro scelte, e
     * ognuna e' stata necessaria per far restare in piedi i corpi (vedi STATO_LAVORO.txt):
     *
     *  1. IL CENTRO E' LA POSA DI RIPOSO (angolo 0), non il punto medio dei limiti del giunto.
     *     Era il difetto piu' grave: un ginocchio con limiti [0, 2.5] ha come punto medio
     *     +1.25 rad, cioe' 72 gradi di flessione, e una spalla -1.05. Il seme metteva ogni corpo
     *     accucciato e contorto - angolo medio 29 gradi, massimo 83 - e da li' cadeva subito.
     *  2. AMPIEZZE IN RADIANTI E SULL'ASSE GIUSTO: l'oscillazione ampia va solo sull'asse che fa
     *     avanzare (quasi parallelo a X, cioe' la flessione avanti-indietro) degli arti che
     *     toccano terra. Sugli altri assi resta piccola, altrimenti il corpo si sbilancia di
     *     fianco. Prima l'ampiezza era una frazione dell'INTERVALLO del giunto, quindi su un
     *     giunto ampio diventava enorme.
     *  3. RITARDO LUNGO L'ARTO: il ginocchio segue l'anca, la caviglia il ginocchio
     *     ({@link BodyTraits#depthOfDof}). E' come si cammina; sul tronco di un corpo che
     *     striscia la stessa regola da' l'onda che lo fa avanzare.
     *  4. CADENZA DAL PENDOLO della gamba, non un numero a caso: una gamba lunga oscilla piano.
     *
     * I due lati restano in CONTROFASE, come in un passo alternato.
     */
    public static Gait seed(Automaton body, Random rng) {
        if (useLegacySeed) {
            return seedLegacy(body, rng);
        }
        BodyTraits traits = BodyTraits.of(body);
        Map<String, Osc> map = new LinkedHashMap<>();
        for (Dof d : body.dofs()) {
            String limb = traits.limbOfDof(d.name);
            boolean support = limb != null && traits.supportLimbs().contains(limb);
            double side = limb == null ? 0 : traits.limbSide(limb);
            boolean sagittal = Math.abs(d.axis.x()) > SAGITTAL_DOT;

            double amp = (support ? (sagittal ? SWING_AMPLITUDE : SUPPORT_SIDE_AMPLITUDE) : FREE_AMPLITUDE)
                    * (0.6 + 0.8 * rng.nextDouble());
            int depth = Math.max(0, traits.depthOfDof(d.name));
            double phase = (side < 0 ? 0 : Math.PI) + depth * LAG_PER_SEGMENT
                    + rng.nextGaussian() * 0.2;
            // il centro e' la posa di riposo; clampOsc lo riporta dentro i limiti se il giunto
            // non arriva a zero (un ginocchio non si estende oltre il diritto)
            double center = rng.nextGaussian() * 0.05;
            map.put(d.name, clampOsc(new Osc(amp, phase, center), d));
        }
        return new Gait(naturalCadence(traits) * (0.85 + 0.3 * rng.nextDouble()), map);
    }

    /**
     * Con true si torna al seme di prima del 23/10/2026, per poter rifare il confronto (e' lo
     * stesso criterio con cui ActionWeights tiene il profilo legacy). Non usarlo in simulazione:
     * centra l'oscillazione sul punto medio dei LIMITI del giunto, quindi parte accucciato.
     */
    public static volatile boolean useLegacySeed = false;

    /** Il seme vecchio, tenuto solo per il confronto. Vedi {@link #useLegacySeed}. */
    private static Gait seedLegacy(Automaton body, Random rng) {
        BodyTraits traits = BodyTraits.of(body);
        Map<String, Osc> map = new LinkedHashMap<>();
        for (Dof d : body.dofs()) {
            String limb = traits.limbOfDof(d.name);
            boolean support = limb != null && traits.supportLimbs().contains(limb);
            double side = limb == null ? 0 : traits.limbSide(limb);
            double span = (d.upper - d.lower) / 2.0;
            double amp = (support ? 0.45 : 0.15) * span * (0.6 + 0.8 * rng.nextDouble());
            double phase = support
                    ? (side < 0 ? 0 : Math.PI) + rng.nextGaussian() * 0.3
                    : rng.nextDouble() * 2 * Math.PI;
            double center = (d.lower + d.upper) / 2.0 + rng.nextGaussian() * span * 0.1;
            map.put(d.name, clampOsc(new Osc(amp, phase, center), d));
        }
        return new Gait(1.0 + rng.nextDouble(), map);
    }

    /**
     * Cadenza naturale di un corpo: quella di un pendolo lungo come la sua gamba. E' la stessa
     * formula della stima geometrica in {@link Locomotion#estimate}, cosi' il punto di partenza
     * dell'andatura e la stima provvisoria della velocita' dicono la stessa cosa.
     */
    private static double naturalCadence(BodyTraits traits) {
        double leg = traits.legLength() > 0 ? traits.legLength() : traits.longestLimb();
        if (leg <= 0.05) {
            return 1.5;
        }
        return 1.0 / (2 * Math.PI) * Math.sqrt(Automaton.GRAVITY / leg);
    }

    /** Andatura tutta casuale (serve per confrontare quanto aiuta il seme strutturato). */
    public static Gait random(Automaton body, Random rng) {
        Map<String, Osc> map = new LinkedHashMap<>();
        for (Dof d : body.dofs()) {
            double span = (d.upper - d.lower) / 2.0;
            map.put(d.name, clampOsc(new Osc(rng.nextDouble() * span,
                    rng.nextDouble() * 2 * Math.PI,
                    d.lower + rng.nextDouble() * (d.upper - d.lower)), d));
        }
        return new Gait(FREQ_MIN + rng.nextDouble() * (FREQ_MAX - FREQ_MIN), map);
    }

    /** Copia con i parametri spostati a caso: e' il passo della mutazione alla nascita. */
    public Gait perturb(Automaton body, Random rng, double sigma) {
        return perturb(body, rng, sigma, 1.0);
    }

    /**
     * Come sopra, ma muovendo solo una FRAZIONE dei giunti, scelta a caso.
     *
     * Serve all'ottimizzazione: un corpo ha 25 gradi di liberta' e tre parametri per ognuno,
     * cioe' 75 numeri, e la simulazione si puo' permettere 4 prove per corpo. Spostarli tutti a
     * ogni prova e' solo rumore; spostarne pochi per volta e' una ricerca per coordinate, che con
     * pochissime prove va molto piu' lontano.
     */
    public Gait perturb(Automaton body, Random rng, double sigma, double fraction) {
        Map<String, Osc> map = new LinkedHashMap<>(oscByDof);
        for (Dof d : body.dofs()) {
            Osc o = map.get(d.name);
            if (o == null) {
                o = new Osc(0, rng.nextDouble() * 2 * Math.PI, 0);
            }
            if (fraction < 1.0 && rng.nextDouble() > fraction) {
                map.put(d.name, clampOsc(o, d));
                continue;
            }
            double span = (d.upper - d.lower) / 2.0;
            Osc moved = new Osc(
                    o.amplitude() + rng.nextGaussian() * sigma * span,
                    o.phase() + rng.nextGaussian() * sigma * Math.PI,
                    o.center() + rng.nextGaussian() * sigma * span);
            map.put(d.name, clampOsc(moved, d));
        }
        double f = frequency * Math.exp(rng.nextGaussian() * sigma * 0.5);
        return new Gait(f, map);
    }

    /**
     * La stessa andatura con l'onda che scorre al contrario: tutte le fasi cambiate di segno.
     *
     * Serve perche' il verso in cui un corpo avanza dipende da come la fase scorre lungo gli arti,
     * e indovinarlo a tavolino non si puo': dipende da dove le mutazioni hanno attaccato gli arti
     * e da come sono orientati i giunti. Misurato: con i motori che seguono davvero l'andatura, il
     * seme strutturato faceva camminare i corpi ALL'INDIETRO (mediana -0.117 m/s). Provare le due
     * versioni costa una corsa di fisica e risolve il problema; cercare un segno globale con
     * quattro perturbazioni casuali non funziona.
     */
    public Gait reversed() {
        Map<String, Osc> map = new LinkedHashMap<>();
        for (Map.Entry<String, Osc> e : oscByDof.entrySet()) {
            Osc o = e.getValue();
            map.put(e.getKey(), new Osc(o.amplitude(), -o.phase(), o.center()));
        }
        return new Gait(frequency, map);
    }

    /**
     * Adatta questa andatura a un corpo diverso (figlio con segmenti in piu' o in meno): i gradi
     * di liberta' con lo stesso nome tengono i loro parametri, quelli nuovi partono dal seme.
     */
    public Gait adaptTo(Automaton body, Random rng) {
        Gait fallback = seed(body, rng);
        Map<String, Osc> map = new LinkedHashMap<>();
        for (Dof d : body.dofs()) {
            Osc mine = oscByDof.get(d.name);
            map.put(d.name, clampOsc(mine != null ? mine : fallback.osc(d.name), d));
        }
        return new Gait(frequency, map);
    }

    /** Incrocio di due andature: per ogni giunto si prende da uno dei due genitori. */
    public static Gait cross(Gait a, Gait b, Automaton body, Random rng) {
        Map<String, Osc> map = new LinkedHashMap<>();
        for (Dof d : body.dofs()) {
            Osc oa = a.oscByDof.get(d.name);
            Osc ob = b.oscByDof.get(d.name);
            Osc pick = oa == null ? ob : ob == null ? oa : (rng.nextBoolean() ? oa : ob);
            if (pick == null) {
                pick = new Osc(0, 0, 0);       // posa di riposo, non il punto medio dei limiti
            }
            map.put(d.name, clampOsc(pick, d));
        }
        double f = rng.nextBoolean() ? a.frequency : b.frequency;
        return new Gait(f, map);
    }

    private static Osc clampOsc(Osc o, Dof d) {
        double span = d.upper - d.lower;
        double amp = Math.max(0, Math.min(span / 2.0, Math.abs(o.amplitude())));
        // il centro va tenuto abbastanza dentro i limiti perche' l'oscillazione non li sfondi
        double lo = d.lower + amp, hi = d.upper - amp;
        double center = lo > hi ? (d.lower + d.upper) / 2.0 : Math.max(lo, Math.min(hi, o.center()));
        double phase = o.phase() % (2 * Math.PI);
        if (phase < 0) {
            phase += 2 * Math.PI;
        }
        return new Osc(amp, phase, center);
    }

    // ------------------------------------------------------------------ uso

    /**
     * Angoli obiettivo di tutti i gradi di liberta' all'istante t (ordine di
     * {@link Automaton#dofs()}), pronti per {@link JoltRagdoll#setTargetAngles}.
     */
    public float[] anglesAt(Automaton body, double t, float[] out) {
        List<Dof> dofs = body.dofs();
        float[] q = out != null && out.length == dofs.size() ? out : new float[dofs.size()];
        double w = 2 * Math.PI * frequency * t;
        for (int i = 0; i < dofs.size(); i++) {
            Dof d = dofs.get(i);
            Osc o = oscByDof.get(d.name);
            double value = o == null ? 0 : o.center() + o.amplitude() * Math.sin(w + o.phase());
            q[i] = (float) Math.max(d.lower, Math.min(d.upper, value));
        }
        return q;
    }

    /** Durata di un ciclo di passo (secondi): serve all'animazione. */
    public double cycleSeconds() {
        return 1.0 / frequency;
    }

    // ------------------------------------------------------------------ JSON

    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        root.put("frequency", frequency);
        JSONArray arr = new JSONArray();
        for (Map.Entry<String, Osc> e : oscByDof.entrySet()) {
            JSONObject o = new JSONObject();
            o.put("dof", e.getKey());
            o.put("amp", e.getValue().amplitude());
            o.put("phase", e.getValue().phase());
            o.put("center", e.getValue().center());
            arr.put(o);
        }
        root.put("oscillators", arr);
        return root;
    }

    public static Gait fromJson(JSONObject obj) {
        Map<String, Osc> map = new LinkedHashMap<>();
        JSONArray arr = obj.optJSONArray("oscillators");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                map.put(o.getString("dof"),
                        new Osc(o.getDouble("amp"), o.getDouble("phase"), o.getDouble("center")));
            }
        }
        return new Gait(obj.optDouble("frequency", 1.0), map);
    }

    /** Nomi dei gradi di liberta' coperti da questa andatura (per diagnostica). */
    public List<String> dofNames() {
        return new ArrayList<>(oscByDof.keySet());
    }
}
