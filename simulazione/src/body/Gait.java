package src.body;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
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
    /**
     * IL RIFLESSO, cioe' la parte di controllo che REAGISCE a come sta il corpo (vedi
     * {@link Reflex}). Sta dentro l'andatura e non a fianco perche' l'andatura e' il genoma del
     * movimento: quello che e' qui dentro si eredita, si incrocia, si muta, si allena durante la
     * vita e finisce nell'archivio, e non e' stato necessario scrivere niente di nuovo per
     * ottenerlo. Null vale come "tutti i pesi a zero", cioe' solo oscillatore.
     */
    private final Reflex reflex;
    /**
     * LA SVOLTA, un numero per grado di liberta': di quanto si sposta il centro di quel giunto
     * quando al corpo si chiede di girare con sterzo = +1 (vedi {@link #targetAngles}).
     *
     * PERCHE' STA QUI E NON IN UNA CLASSE A PARTE: girare e' locomozione come camminare, quindi
     * appartiene al genoma del movimento - si eredita, si incrocia, si muta, si allena e finisce
     * nell'archivio con tutto il resto, senza che serva scrivere una macchina nuova. E' la stessa
     * ragione per cui il riflesso sta qui dentro.
     *
     * PARTE TUTTA A ZERO, quindi un'andatura appena creata si comporta ESATTAMENTE come prima: la
     * svolta e' un caso particolare dell'andatura di sempre, non un esperimento a fianco.
     *
     * COSA IMPONE QUESTA FORMA, dichiarato: un solo numero per giunto, e girare a sinistra e'
     * sterzo = -1, cioe' lo SPECCHIO di girare a destra. Non e' una verita' sui corpi (un corpo
     * asimmetrico gira meglio da un lato), e' una compressione dello spazio di ricerca - la
     * stessa scelta dell'oscillatore, che impone una sinusoide. Un blocco per verso raddoppierebbe
     * i parametri; questa versione li tiene a uno per giunto e l'asimmetria la si MISURA invece di
     * assumerla (vedi GaitEvaluator.Turn.yawOpposta).
     * Null vale come "tutti a zero", cioe' un corpo che non sa girare.
     */
    private final Map<String, Double> sterzoByDof;

    private Gait(double frequency, Map<String, Osc> oscByDof) {
        this(frequency, oscByDof, null, null);
    }

    private Gait(double frequency, Map<String, Osc> oscByDof, Reflex reflex) {
        this(frequency, oscByDof, reflex, null);
    }

    private Gait(double frequency, Map<String, Osc> oscByDof, Reflex reflex,
                 Map<String, Double> sterzoByDof) {
        this.frequency = Math.max(FREQ_MIN, Math.min(FREQ_MAX, frequency));
        this.oscByDof = oscByDof;
        this.reflex = reflex;
        this.sterzoByDof = sterzoByDof;
    }

    /**
     * 1 = l'oscillatore contribuisce come sempre. 0 = L'OSCILLATORE E' SOLO UN OROLOGIO: gli
     * angoli li decide il riflesso, e il ciclo resta disponibile come due canali di ingresso
     * (seno e coseno della fase). E' la forma completamente autonoma, dove il movimento non e'
     * ricevuto ma generato - da misurare contro 1, non da dare per buona.
     */
    public static volatile double oscWeight = 1.0;

    /**
     * Se il riflesso viene applicato. PREDEFINITO SPENTO, e il motivo e' una misura, non una
     * preferenza.
     *
     * Misurato il 24/10/2026 su 78 corpi evoluti appaiati (stessi corpi, stessi semi,
     * src.body.EvolvedBodies, 1.5 s per prova; il confronto corpo per corpo sta in
     * STATO_LAVORO.txt voce 21):
     *
     *   oscillatore 24 prove      +0.717 m/s   59 cadute   <- il braccio di controllo
     *   riflesso    24 prove      +0.567 m/s   56 cadute   28/78, p = 0.017 CONTRO il riflesso
     *   oscillatore 48 prove      +0.903 m/s   45 cadute
     *   riflesso    48 prove      +0.851 m/s   55 cadute   cadute: 12 contro 2, p = 0.013 contro
     *
     * A PARI PROVE il riflesso non guadagna velocita' e PERDE sulle cadute. Quello che a prima
     * vista sembrava un guadagno (48 prove con riflesso contro 24 senza, p = 0.031 a favore) era
     * il budget di prove, non il riflesso: l'oscillatore da solo, passando da 24 a 48 prove, va
     * da +0.717 a +0.903 con p = 0.0001.
     *
     * La diagnosi non e' che i sensi siano sbagliati: e' che la SALITA DELLA COLLINA non sa
     * cercare in questo spazio. L'oscillatore ha ~75 parametri, il riflesso ~350, e muoverne un
     * sottoinsieme a caso una prova per volta non basta - si vede nei pesi, che restano quasi
     * uniformi sui quattordici canali (7-10% ciascuno) invece di concentrarsi su qualcuno.
     * Quindi: l'apparato resta, completo e misurabile, e si accende quando c'e' una ricerca
     * capace di reggerlo. Spento, {@link #perturb} non tocca i pesi e il percorso e'
     * IDENTICO CIFRA PER CIFRA a quello di prima del rifacimento (verificato sui binari di HEAD:
     * +0.717 m/s e 59 cadute, gli stessi numeri).
     */
    public static volatile boolean useReflex = false;

    /** Lo spostamento di centro del giunto per sterzo = +1 (0 se questo corpo non sa girare). */
    public double sterzo(String dofName) {
        Double v = sterzoByDof == null ? null : sterzoByDof.get(dofName);
        return v == null ? 0.0 : v;
    }

    /** Tutta la svolta, per diagnostica e per la firma della misura. */
    public Map<String, Double> sterzi() {
        return sterzoByDof == null ? Map.of() : Collections.unmodifiableMap(sterzoByDof);
    }

    /** Vero se questo corpo non ha ancora nessuna svolta (tutti gli spostamenti a zero). */
    public boolean sterzoNullo() {
        if (sterzoByDof == null) {
            return true;
        }
        for (double v : sterzoByDof.values()) {
            if (v != 0) {
                return false;
            }
        }
        return true;
    }

    /** La stessa andatura con questa svolta al posto della sua. */
    public Gait withSterzo(Map<String, Double> sterzo) {
        return new Gait(frequency, oscByDof, reflex, sterzo == null ? null : new LinkedHashMap<>(sterzo));
    }

    /**
     * La stessa andatura con la svolta rovesciata di segno.
     *
     * Serve a fissare la CONVENZIONE: la ricerca trova uno spostamento dei centri che fa girare il
     * corpo, ma da che parte lo giri non lo si puo' sapere prima di provarlo (dipende da dove le
     * mutazioni hanno attaccato gli arti). Se la svolta trovata gira in negativo si rovescia, e
     * cosi' sterzo = +1 vuol dire sempre lo stesso verso per tutti i corpi - che e' la condizione
     * perche' l'entita' possa chiedere "a destra" e ottenere destra.
     */
    public Gait sterzoRovesciato() {
        if (sterzoByDof == null) {
            return this;
        }
        Map<String, Double> map = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : sterzoByDof.entrySet()) {
            map.put(e.getKey(), -e.getValue());
        }
        return new Gait(frequency, oscByDof, reflex, map);
    }

    public Reflex reflex() {
        return reflex;
    }

    /** La stessa andatura con un altro riflesso (l'oscillatore non si tocca). */
    public Gait withReflexKeepSterzo(Reflex r) {
        return new Gait(frequency, oscByDof, r, sterzoByDof);
    }

    public Gait withReflex(Reflex r) {
        return new Gait(frequency, oscByDof, r);
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
        /*
         * UN BLOCCO PER VOLTA: una prova di fisica sposta O l'oscillatore O il riflesso, mai tutti
         * e due insieme. Il motivo e' che il punteggio di una prova e' UN NUMERO SOLO: se in quella
         * prova si sono mossi insieme ampiezze, fasi, frequenza e qualche decina di pesi, quel
         * numero non dice quale dei due ha fatto la differenza, e la salita della collina si
         * ritrova a cercare in uno spazio due volte piu' grande con la stessa informazione di
         * prima. Alternando, ogni prova e' una domanda su una cosa sola.
         * REFLEX_SHARE e' quante prove vanno al riflesso: e' un budget diviso, non una preferenza.
         */
        boolean tastaRiflesso = useReflex && rng.nextDouble() < REFLEX_SHARE;
        double fracOsc = tastaRiflesso ? 0.0 : fraction;
        Map<String, Osc> map = new LinkedHashMap<>(oscByDof);
        for (Dof d : body.dofs()) {
            Osc o = map.get(d.name);
            if (o == null) {
                o = new Osc(0, rng.nextDouble() * 2 * Math.PI, 0);
            }
            if (fracOsc <= 0.0 || (fracOsc < 1.0 && rng.nextDouble() > fracOsc)) {
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
        double f = tastaRiflesso ? frequency : frequency * Math.exp(rng.nextGaussian() * sigma * 0.5);
        /*
         * IL RIFLESSO SI MUTA INSIEME ALL'OSCILLATORE, ma con un passo suo: i pesi sono in
         * radianti per unita' di canale (i canali sono normalizzati a un intorno di 1), quindi
         * REFLEX_SIGMA e' quanta correzione in piu' o in meno un peso puo' prendere in un colpo.
         * E con una frazione piu' piccola, perche' i pesi sono molti piu' dei parametri
         * dell'oscillatore e spostarne tanti insieme e' rumore.
         */
        Reflex r = reflex != null ? reflex : Reflex.zero(body);
        if (tastaRiflesso) {
            r = r.perturb(body, rng, sigma * REFLEX_SIGMA, Math.min(1.0, fraction * REFLEX_FRACTION));
        }
        // la svolta non si tocca qui: ha la sua ricerca e il suo punteggio (vedi perturbSterzo)
        return new Gait(f, map, r, sterzoByDof);
    }

    /**
     * Copia con la SOLA SVOLTA spostata a caso: e' il passo della ricerca della svolta.
     *
     * E' un metodo a parte e non un terzo blocco di {@link #perturb} per una ragione di misura:
     * una prova che cerca la svolta si punteggia sull'IMBARDATA, una che cerca il passo si
     * punteggia sull'AVANZARE. Sono due domande diverse con due risposte diverse, quindi due
     * ricerche - mescolarle vorrebbe dire spostare la svolta in una prova che non la guarda
     * nemmeno, cioe' buttare la prova.
     *
     * Lo spostamento e' in frazione dell'ESCURSIONE del giunto, come i centri dell'oscillatore, e
     * resta dentro i limiti: chiedere a un giunto un angolo fuori corsa non lo fa girare di piu',
     * fa strappare il motore.
     */
    public Gait perturbSterzo(Automaton body, Random rng, double sigma, double fraction) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (Dof d : body.dofs()) {
            double v = sterzo(d.name);
            if (fraction < 1.0 && rng.nextDouble() > fraction) {
                map.put(d.name, v);
                continue;
            }
            double span = (d.upper - d.lower) / 2.0;
            double moved = v + rng.nextGaussian() * sigma * span;
            // la svolta non puo' da sola portare un giunto fuori dalla sua corsa
            map.put(d.name, Math.max(-span, Math.min(span, moved)));
        }
        return new Gait(frequency, oscByDof, reflex, map);
    }

    /** Quanto si sposta un peso del riflesso, in rapporto alla sigma dell'oscillatore. */
    public static volatile double REFLEX_SIGMA = 0.8;
    /** Quanta parte dei pesi si muove, in rapporto alla frazione dell'oscillatore. */
    public static volatile double REFLEX_FRACTION = 0.35;
    /** Quante prove di fisica vanno a tastare il riflesso invece dell'oscillatore. */
    public static volatile double REFLEX_SHARE = 0.5;

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
        return new Gait(frequency, map, reflex, sterzoByDof);
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
        /*
         * LA SVOLTA SI EREDITA PER NOME, come l'oscillatore: un giunto che il figlio ha in comune
         * col genitore tiene lo spostamento imparato, un giunto NUOVO parte da zero. Non si
         * inventa una svolta per un arto che il genitore non aveva - quella la deve trovare la
         * ricerca.
         */
        Map<String, Double> st = new LinkedHashMap<>();
        for (Dof d : body.dofs()) {
            double span = (d.upper - d.lower) / 2.0;
            st.put(d.name, Math.max(-span, Math.min(span, sterzo(d.name))));
        }
        return new Gait(frequency, map, reflex == null ? null : reflex.adaptTo(body), st);
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
        /*
         * LA SVOLTA SI INCROCIA PER GIUNTO INTERO, come tutto il resto: lo spostamento di un'anca
         * e' una cosa sola, e prenderne una parte da un genitore e una dall'altro vorrebbe dire
         * inventare un terzo modo di girare che nessuno dei due ha provato.
         */
        Map<String, Double> st = new LinkedHashMap<>();
        for (Dof d : body.dofs()) {
            double span = (d.upper - d.lower) / 2.0;
            double v = rng.nextBoolean() ? a.sterzo(d.name) : b.sterzo(d.name);
            st.put(d.name, Math.max(-span, Math.min(span, v)));
        }
        return new Gait(f, map, Reflex.cross(a.reflex, b.reflex, body, rng), st);
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

    /**
     * GLI ANGOLI OBIETTIVO VERI: oscillatore piu' riflesso.
     *
     * E' questo che va dato ai motori quando il corpo e' dentro la fisica. {@link #anglesAt} resta
     * per il disegno (il motore grafico non ha un corpo fisico da interrogare, quindi vede solo il
     * ciclo); qui c'e' anche la parte che reagisce, e con {@link #oscWeight} a 0 c'e' SOLO quella.
     *
     * @param sensi stato del corpo, gia' letto per questo passo di controllo (vedi
     *              {@link MotorSense#read}); null = non disponibile, vale il solo oscillatore
     */
    public float[] targetAngles(Automaton body, double t, MotorSense sensi, float[] out) {
        return targetAngles(body, t, sensi, 0.0, out);
    }

    /**
     * Come sopra, chiedendo al corpo di GIRARE: sterzo va da -1 a +1, 0 = dritto.
     *
     * La svolta e' uno spostamento dei centri dei giunti, proporzionale allo sterzo, SOPRA
     * l'oscillazione che resta quella di sempre. Cioe' il corpo continua a fare il suo passo e
     * intanto tiene i giunti un po' spostati da un lato: come ci gira - inclinandosi, accorciando
     * un arto, puntando il piede - lo decide la ricerca, non questa riga.
     *
     * NON C'E' NESSUN CONTROLLO DI DIREZIONE QUI: nessuno misura dove il corpo e' girato per
     * correggerlo. Lo sterzo e' una RICHIESTA, e quanto ne esce dipende dal corpo - esattamente
     * come i metri camminati.
     */
    public float[] targetAngles(Automaton body, double t, MotorSense sensi, double sterzo,
                                float[] out) {
        List<Dof> dofs = body.dofs();
        float[] q = out != null && out.length == dofs.size() ? out : new float[dofs.size()];
        double w = 2 * Math.PI * frequency * t;
        double peso = Math.max(0, oscWeight);
        double u = Math.max(-1.0, Math.min(1.0, sterzo));
        for (int i = 0; i < dofs.size(); i++) {
            Dof d = dofs.get(i);
            Osc o = oscByDof.get(d.name);
            double value = o == null ? 0
                    : o.center() + peso * o.amplitude() * Math.sin(w + o.phase());
            if (u != 0) {
                value += u * sterzo(d.name);
            }
            q[i] = (float) Math.max(d.lower, Math.min(d.upper, value));
        }
        if (useReflex && reflex != null && sensi != null) {
            reflex.apply(body, sensi, q);
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
        if (reflex != null && !reflex.isZero()) {
            root.put("riflesso", reflex.toJson());
        }
        if (!sterzoNullo()) {
            JSONObject st = new JSONObject();
            for (Map.Entry<String, Double> e : sterzoByDof.entrySet()) {
                if (e.getValue() != 0) {
                    st.put(e.getKey(), e.getValue());
                }
            }
            root.put("sterzo", st);
        }
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
        Reflex r = obj.has("riflesso") ? Reflex.fromJson(obj.getJSONObject("riflesso")) : null;
        Map<String, Double> st = null;
        JSONObject sj = obj.optJSONObject("sterzo");
        if (sj != null) {
            st = new LinkedHashMap<>();
            for (String k : sj.keySet()) {
                st.put(k, sj.getDouble(k));
            }
        }
        return new Gait(obj.optDouble("frequency", 1.0), map, r, st);
    }

    /** Nomi dei gradi di liberta' coperti da questa andatura (per diagnostica). */
    public List<String> dofNames() {
        return new ArrayList<>(oscByDof.keySet());
    }
}
