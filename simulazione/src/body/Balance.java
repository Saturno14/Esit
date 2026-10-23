package src.body;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Riflesso di equilibrio: la correzione da sommare agli angoli dell'andatura quando il corpo si
 * inclina.
 *
 * PERCHE' SERVE. L'oscillatore di {@link Gait} e' a CICLO APERTO: ripete sempre lo stesso ciclo
 * senza sapere come sta il corpo. Misurato su 78 corpi evoluti, con un ciclo aperto cadono 75-76
 * su 78 qualunque sia il seme - sistemare la posa di partenza ha triplicato la velocita' in avanti
 * (+0.119 -> +0.445 m/s sui bipedi) ma non ha salvato nessuno dalla caduta. Un bipede non si
 * bilancia senza reagire a come sta cadendo: e' questo che aggiunge questa classe.
 *
 * COME. Si misura di quanto il tronco e' fuori piombo (beccheggio = inclinato avanti/indietro,
 * rollio = inclinato di fianco) e si sposta ogni giunto degli arti che portano il peso nel verso
 * che riporta il piede sotto il corpo, con un termine proporzionale all'inclinazione e uno alla
 * sua velocita' (un classico PD).
 *
 * IL VERSO NON E' SCRITTO A MANO, e non potrebbe esserlo: i corpi hanno forme qualsiasi, con arti
 * attaccati dove le mutazioni hanno deciso. Si ricava dalla geometria. Ruotando di un angolo
 * piccolo t attorno all'asse a, la punta dell'arto (a distanza r dal perno) si sposta di
 * t * (a x r). Quindi per spostare il piede INDIETRO quando il corpo cade in avanti basta il
 * segno di (a x r) . z, e lo stesso con x per il fianco. Funziona su un'anca umanoide come su un
 * arto nato da una mutazione.
 */
public final class Balance {

    /** Quanto conta l'inclinazione (radianti di correzione per radiante fuori piombo). */
    public static volatile double KP = 1.2;
    /** Quanto conta la velocita' con cui si inclina (radianti per radiante al secondo). */
    public static volatile double KD = 0.12;
    /** Tetto della correzione, per non far esplodere i motori (rad). */
    public static volatile double MAX_CORRECTION = 0.6;

    private final double[] wPitch;
    private final double[] wRoll;

    private Balance(double[] wPitch, double[] wRoll) {
        this.wPitch = wPitch;
        this.wRoll = wRoll;
    }

    /** Pesi del riflesso per ogni grado di liberta', nell'ordine di {@link Automaton#dofs()}. */
    public static Balance of(Automaton body) {
        BodyTraits traits = BodyTraits.of(body);
        BodyPose rest = body.pose(null, V3.ZERO, Rot.IDENTITY);
        Map<String, V3> limbTip = limbTips(body, traits, rest);

        int n = body.dofCount();
        double[] wPitch = new double[n];
        double[] wRoll = new double[n];
        Map<String, Joint> jointByName = new LinkedHashMap<>();
        for (Joint j : body.joints()) {
            jointByName.put(j.name(), j);
        }

        int i = 0;
        for (Dof d : body.dofs()) {
            String limb = traits.limbOfDof(d.name);
            // solo gli arti che portano il peso: le braccia non raddrizzano niente
            if (limb == null || !traits.supportLimbs().contains(limb)) {
                i++;
                continue;
            }
            Joint j = jointOf(jointByName, d.name);
            V3 tip = limbTip.get(limb);
            if (j == null || tip == null) {
                i++;
                continue;
            }
            BodyPose.LinkPose child = rest.get(j.child());
            if (child == null) {
                i++;
                continue;
            }
            // asse nel mondo: l'asse del giunto e' scritto negli assi del genitore
            Rot parentRot = parentRotation(body, rest, j);
            V3 axis = parentRot.apply(d.axis).normalized();
            V3 r = tip.sub(child.origin());
            double len = r.length();
            if (len < 1e-6) {
                i++;
                continue;
            }
            V3 move = axis.cross(r).scale(1.0 / len);   // spostamento della punta per radiante
            wPitch[i] = -move.z();
            wRoll[i] = -move.x();
            i++;
        }
        return new Balance(wPitch, wRoll);
    }

    /**
     * Correzione da sommare agli angoli obiettivo. Gli angoli vengono modificati sul posto e
     * tenuti dentro i limiti dei giunti.
     *
     * @param pitch     inclinazione avanti/indietro del tronco (rad, 0 = in piombo)
     * @param roll      inclinazione di fianco (rad)
     * @param pitchRate velocita' del beccheggio (rad/s)
     * @param rollRate  velocita' del rollio (rad/s)
     */
    public void apply(Automaton body, float[] q, double pitch, double roll,
                      double pitchRate, double rollRate) {
        int i = 0;
        for (Dof d : body.dofs()) {
            if (i >= q.length) {
                break;
            }
            if (wPitch[i] != 0 || wRoll[i] != 0) {
                double c = KP * (wPitch[i] * pitch + wRoll[i] * roll)
                        + KD * (wPitch[i] * pitchRate + wRoll[i] * rollRate);
                c = Math.max(-MAX_CORRECTION, Math.min(MAX_CORRECTION, c));
                q[i] = (float) Math.max(d.lower, Math.min(d.upper, q[i] + c));
            }
            i++;
        }
    }

    /**
     * Di quanto il corpo e' fuori piombo, dalla rotazione del tronco: l'asse "su" del corpo
     * portato nel mondo dice da che parte pende. [beccheggio, rollio] in radianti.
     */
    public static double[] tilt(Rot rootRotation) {
        V3 up = rootRotation.apply(new V3(0, 1, 0));
        double pitch = Math.atan2(up.z(), Math.max(1e-6, up.y()));
        double roll = Math.atan2(up.x(), Math.max(1e-6, up.y()));
        return new double[]{pitch, roll};
    }

    // ------------------------------------------------------------------ interno

    /** La punta piu' bassa di ogni arto di appoggio: e' il piede, quello che va rimesso sotto. */
    private static Map<String, V3> limbTips(Automaton body, BodyTraits traits, BodyPose rest) {
        Map<String, V3> tips = new LinkedHashMap<>();
        Map<String, Double> best = new LinkedHashMap<>();
        for (Link l : body.links()) {
            String limb = traits.limbOfLink(l.name);
            if (limb == null || !traits.supportLimbs().contains(limb)) {
                continue;
            }
            BodyPose.LinkPose p = rest.get(l.name);
            V3 low = p.coreA().y() <= p.coreB().y() ? p.coreA() : p.coreB();
            Double cur = best.get(limb);
            if (cur == null || low.y() < cur) {
                best.put(limb, low.y());
                tips.put(limb, low);
            }
        }
        return tips;
    }

    private static Joint jointOf(Map<String, Joint> jointByName, String dofName) {
        for (Map.Entry<String, Joint> e : jointByName.entrySet()) {
            if (dofName.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static Rot parentRotation(Automaton body, BodyPose rest, Joint j) {
        Link child = body.link(j.child());
        if (child == null || child.parent == null) {
            return Rot.IDENTITY;
        }
        BodyPose.LinkPose p = rest.get(child.parent);
        return p == null ? Rot.IDENTITY : p.rot();
    }
}
