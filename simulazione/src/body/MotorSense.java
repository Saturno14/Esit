package src.body;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * I SENSI DEL MOVIMENTO: quello che un corpo sa di se' stesso, istante per istante, e che il suo
 * riflesso ({@link Reflex}) puo' usare per decidere come muovere i giunti.
 *
 * PERCHE' ESISTE QUESTA CLASSE. Prima il controllo del movimento era di due pezzi: un oscillatore
 * a ciclo aperto, che ripete sempre lo stesso ciclo senza sapere niente, e un riflesso di
 * equilibrio SCRITTO A MANO (la vecchia classe Balance) con gli stessi guadagni per tutti i corpi
 * e una sola strategia - "rimetti il piede sotto il tronco". Quel riflesso era un oracolo: non
 * appiattiva soltanto le andature, SCEGLIEVA quali morfologie potessero funzionare, perche'
 * presuppone piedi e un poligono di appoggio. Su un bipede ha senso, su chi striscia non vuol dire
 * niente, e cosi' la selezione finiva per premiare i corpi che somigliavano a quello che il
 * controllore si aspettava - cioe' il contrario di quello che questo progetto cerca.
 *
 * Qui invece non c'e' nessuna strategia. Ci sono soltanto gli INGRESSI: dove sta ogni giunto,
 * quanto velocemente si muove, quale segmento tocca terra, dove sta andando il baricentro, di
 * quanto il tronco e' fuori piombo, e un orologio. Cosa farne lo decidono i pesi del riflesso, che
 * si ereditano, si incrociano, si mutano e si allenano come ampiezza e fase dell'oscillatore. Un
 * bipede puo' scoprire "se cado in avanti porta avanti la gamba"; un quadrupede qualcos'altro; chi
 * striscia porta a zero i pesi dei contatti perche' non ha appoggi da leggere. Stesso meccanismo
 * per tutti, esito diverso per ciascuno.
 *
 * I CANALI SONO LOCALI, e non e' un dettaglio di prestazioni. Ogni grado di liberta' vede il
 * proprio angolo, la propria velocita', il contatto del PROPRIO arto e quello dell'arto opposto,
 * piu' una manciata di grandezze globali (assetto, baricentro, orologio). Con una rete tutta
 * connessa un corpo da 25 gradi di liberta' avrebbe circa 1800 pesi: impossibili da cercare con
 * una prova di fisica per volta, e impossibili da tenere in archivio. Con i canali locali sono
 * {@link #CHANNELS} per giunto, cioe' qualche centinaio, ed e' uno spazio in cui la salita della
 * collina che il progetto usa gia' riesce davvero a muoversi. Il fatto che un riflesso sia locale
 * e' anche l'unica cosa che somiglia a come sono fatti i riflessi veri.
 *
 * L'arto opposto c'e' per una ragione precisa: senza di esso l'alternanza (quando questa zampa
 * tocca, quella si alza) non e' esprimibile, e l'alternanza e' il minimo indispensabile perche'
 * un passo esista.
 *
 * NORMALIZZAZIONE. Ogni canale e' riportato a un intorno di 1 (angoli sulla loro corsa, velocita'
 * su un valore di riferimento). Senza questo i pesi dei canali grandi dominerebbero, e la
 * mutazione - che e' gaussiana con la stessa sigma per tutti - sposterebbe molto certi canali e
 * niente altri: la ricerca sarebbe storta prima di cominciare.
 */
public final class MotorSense {

    /** Quanti canali vede ogni grado di liberta'. Vedi le costanti qui sotto. */
    public static final int CHANNELS = 14;

    public static final int C_BIAS = 0;
    public static final int C_ANGLE = 1;
    public static final int C_RATE = 2;
    public static final int C_CONTACT_OWN = 3;
    public static final int C_CONTACT_OTHER = 4;
    public static final int C_PITCH = 5;
    public static final int C_ROLL = 6;
    public static final int C_PITCH_RATE = 7;
    public static final int C_ROLL_RATE = 8;
    public static final int C_VEL_FWD = 9;
    public static final int C_VEL_SIDE = 10;
    public static final int C_HEIGHT = 11;
    public static final int C_CLOCK_SIN = 12;
    public static final int C_CLOCK_COS = 13;

    /** Nomi dei canali, per la diagnostica (comando "riflesso"). */
    public static final String[] NAMES = {
            "bias", "angolo", "velocita'", "contatto proprio", "contatto opposto",
            "beccheggio", "rollio", "vel.beccheggio", "vel.rollio",
            "vel.avanti", "vel.laterale", "altezza", "orologio.sin", "orologio.cos"
    };

    /** Velocita' angolare di riferimento per normalizzare le velocita' dei giunti (rad/s). */
    private static final double RATE_REF = 6.0;
    /** Velocita' di riferimento del baricentro (m/s). */
    private static final double VEL_REF = 1.0;
    /** Quanto sopra il pavimento un segmento conta come "appoggiato" (m). */
    private static final double CONTACT_MARGIN = 0.03;

    private final Automaton body;
    private final int dofCount;

    /** Per ogni grado di liberta': l'arto cui appartiene, e l'arto dal lato opposto (-1 = nessuno). */
    private final int[] ownLimb;
    private final int[] otherLimb;
    private final double[] span;      // corsa di ogni giunto, per normalizzare l'angolo

    private final List<String> limbs = new ArrayList<>();
    private final Map<String, List<Integer>> linksOfLimb = new LinkedHashMap<>();

    private final boolean[] contattiLink;
    private final boolean[] contattoArto;

    private float[] angoli;
    private float[] velocita;
    private double pitch, roll, pitchRate, rollRate;
    private double velFwd, velSide, heightRatio;
    private double clockSin, clockCos;
    private double altezzaRiposo = -1;

    public MotorSense(Automaton body) {
        this.body = body;
        this.dofCount = body.dofCount();
        this.ownLimb = new int[dofCount];
        this.otherLimb = new int[dofCount];
        this.span = new double[dofCount];
        this.contattiLink = new boolean[body.links().size()];

        BodyTraits traits = BodyTraits.of(body);
        // gli arti nell'ordine in cui compaiono, e per ognuno i suoi segmenti
        for (Link l : body.links()) {
            String limb = traits.limbOfLink(l.name);
            if (limb == null) {
                continue;
            }
            if (!linksOfLimb.containsKey(limb)) {
                linksOfLimb.put(limb, new ArrayList<>());
                limbs.add(limb);
            }
            linksOfLimb.get(limb).add(body.links().indexOf(l));
        }
        this.contattoArto = new boolean[limbs.size()];

        List<Dof> dofs = body.dofs();
        for (int i = 0; i < dofCount; i++) {
            Dof d = dofs.get(i);
            span[i] = Math.max(0.1, (d.upper - d.lower) / 2.0);
            String limb = traits.limbOfDof(d.name);
            ownLimb[i] = limb == null ? -1 : limbs.indexOf(limb);
            otherLimb[i] = limb == null ? -1 : limbs.indexOf(oppostoDi(traits, limb));
        }
    }

    /**
     * L'arto "dall'altra parte": il primo arto di appoggio il cui lato ha segno opposto. Se non
     * esiste (un corpo con un solo arto, o tutti dalla stessa parte) il canale resta spento, e il
     * riflesso se ne accorge perche' il peso di un canale sempre a zero non cambia niente.
     */
    private String oppostoDi(BodyTraits traits, String limb) {
        double mio = traits.limbSide(limb);
        String primoAltro = null;
        for (String altro : traits.supportLimbs()) {
            if (altro.equals(limb)) {
                continue;
            }
            if (primoAltro == null) {
                primoAltro = altro;
            }
            if (mio * traits.limbSide(altro) < 0) {
                return altro;
            }
        }
        // nessuno dall'altro lato: va bene un altro arto di appoggio qualunque, che e' comunque
        // un'informazione sull'alternanza
        return primoAltro;
    }

    // ------------------------------------------------------------------ lettura

    /**
     * Legge lo stato del corpo dal mondo fisico. Da chiamare una volta per passo di controllo,
     * prima di {@link Reflex#apply}.
     *
     * @param groundY quota del pavimento
     * @param clock   fase dell'orologio in radianti (quella dell'oscillatore: cosi' il riflesso
     *                puo' usare il ritmo che c'e' gia', o costruirsene uno suo)
     */
    public void read(JoltRagdoll doll, double groundY, double clock) {
        angoli = doll.jointAngles(angoli);
        velocita = doll.jointRates(velocita);

        double[] t = doll.rootRotation().tilt();
        pitch = t[0];
        roll = t[1];
        V3 w = doll.rootAngularVelocity();
        // il beccheggio e' la rotazione attorno a X, il rollio quella attorno a Z: le stesse due
        // componenti che tilt() misura come angoli
        pitchRate = w.x();
        rollRate = w.z();

        V3 v = doll.comVelocity();
        /*
         * LE VELOCITA' VANNO NEGLI ASSI DEL CORPO, non del mondo. Un riflesso che leggesse "sto
         * andando verso +Z del mondo" dovrebbe imparare una cosa diversa per ogni direzione in cui
         * il corpo e' girato, cioe' non potrebbe impararla. Girato di schiena, "avanti" deve
         * restare avanti.
         */
        V3 locale = doll.rootRotation().inverse().apply(v);
        velFwd = locale.z() / VEL_REF;
        velSide = locale.x() / VEL_REF;

        V3 com = doll.centerOfMass();
        if (altezzaRiposo <= 0) {
            altezzaRiposo = Math.max(0.05, com.y() - groundY);
        }
        // centrato su zero: 0 = come al momento di partenza, negativo = si e' accucciato o caduto
        heightRatio = (com.y() - groundY) / altezzaRiposo - 1.0;

        doll.readContacts(groundY, CONTACT_MARGIN, contattiLink);
        for (int a = 0; a < contattoArto.length; a++) {
            contattoArto[a] = false;
        }
        for (int a = 0; a < limbs.size(); a++) {
            for (int idx : linksOfLimb.get(limbs.get(a))) {
                if (idx < contattiLink.length && contattiLink[idx]) {
                    contattoArto[a] = true;
                    break;
                }
            }
        }

        clockSin = Math.sin(clock);
        clockCos = Math.cos(clock);
    }

    /** Il valore del canale {@code c} come lo vede il grado di liberta' {@code dof}. */
    public double channel(int dof, int c) {
        switch (c) {
            case C_BIAS: return 1.0;
            case C_ANGLE: return angoli == null ? 0 : angoli[dof] / span[dof];
            case C_RATE: return velocita == null ? 0 : velocita[dof] / RATE_REF;
            case C_CONTACT_OWN: return ownLimb[dof] >= 0 && contattoArto[ownLimb[dof]] ? 1 : 0;
            case C_CONTACT_OTHER: return otherLimb[dof] >= 0 && contattoArto[otherLimb[dof]] ? 1 : 0;
            case C_PITCH: return pitch;
            case C_ROLL: return roll;
            case C_PITCH_RATE: return pitchRate / RATE_REF;
            case C_ROLL_RATE: return rollRate / RATE_REF;
            case C_VEL_FWD: return velFwd;
            case C_VEL_SIDE: return velSide;
            case C_HEIGHT: return heightRatio;
            case C_CLOCK_SIN: return clockSin;
            case C_CLOCK_COS: return clockCos;
            default: return 0;
        }
    }

    public int dofCount() {
        return dofCount;
    }

    public Automaton body() {
        return body;
    }

    /** Quanti segmenti stanno toccando terra (diagnostica e punteggio). */
    public int contattiAttivi() {
        int n = 0;
        for (boolean b : contattiLink) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    /** Riga leggibile dello stato: serve al comando di diagnostica e a capire cosa vede un corpo. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(java.util.Locale.ROOT,
                "beccheggio %.2f rollio %.2f (vel %.2f/%.2f)  baricentro avanti %.2f lato %.2f "
                        + "altezza %+.2f  contatti %d/%d",
                pitch, roll, pitchRate, rollRate, velFwd, velSide, heightRatio,
                contattiAttivi(), contattiLink.length));
        return sb.toString();
    }
}
