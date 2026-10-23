package src.body;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dove guarda un'entita' e quanto puo' girare la testa senza girare il corpo.
 *
 * NIENTE E' SCRITTO A MANO, perche' non potrebbe esserlo: i corpi nascono da mutazioni e non
 * hanno un numero fisso di segmenti. La testa e la portata del collo si DEDUCONO dal corpo, e
 * cambiano quando il corpo cambia.
 *
 * LA TESTA e' l'estremita' piu' alta che non serve ad appoggiarsi: in un umanoide e' il cranio,
 * in un corpo che striscia e' la punta del tronco (un serpente guarda dove punta la testa, che e'
 * l'unica estremita' che ha). Un corpo che non ha nessuna estremita' libera guarda dove punta il
 * corpo e non puo' girarsi a guardare: e' un vincolo vero, non un ripiego.
 *
 * LA PORTATA DEL COLLO e' la somma dei limiti dei giunti che stanno tra il tronco e la testa,
 * pesata su quanto ogni asse di rotazione e' verticale (girare la testa di lato e' una rotazione
 * attorno alla verticale). Quei limiti stanno nel piano del corpo e MUTANO, quindi la portata e'
 * genetica: un collo lungo e flessibile fa vedere intorno, uno rigido no, e a deciderlo e'
 * l'evoluzione invece di un numero che ho scelto io.
 *
 * Si tiene il lato PIU' CORTO di ogni giunto (il minimo tra i due limiti) perche' la portata
 * dev'essere simmetrica: un'entita' che puo' girare la testa 90 gradi a destra e 10 a sinistra
 * non ha un campo di scansione di 100 gradi centrato sul naso.
 */
public final class Gaze {

    /**
     * @param head     segmento che porta gli occhi
     * @param yawRange quanto si gira la testa da una parte (radianti, 0 = collo rigido)
     * @param yawDofs  gradi di liberta' su cui si distribuisce la rotazione, con il loro peso
     */
    public record Head(String head, double yawRange, List<YawDof> yawDofs) {

        /** True se l'entita' puo' guardarsi intorno senza girare il corpo. */
        public boolean canTurn() {
            return yawRange > 1e-3 && !yawDofs.isEmpty();
        }
    }

    /**
     * @param index  posizione nel vettore degli angoli del corpo (vedi Automaton.dofs)
     * @param share  quanta parte della rotazione dello sguardo tocca a questo giunto
     * @param limit  limite del giunto su quel verso (radianti, sempre positivo)
     */
    public record YawDof(int index, double share, double limit) {
    }

    /** Oltre mezzo giro per lato non si va, qualunque cosa dica il genoma. */
    private static final double MAX_YAW = Math.PI / 2;

    private Gaze() {
    }

    public static Head of(Automaton body, BodyTraits traits) {
        String head = findHead(body, traits);
        if (head == null) {
            return new Head(body.links().get(0).name, 0, List.of());
        }

        // giunti tra la testa e il tronco, dal basso verso l'alto
        List<Dof> yaw = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        String link = head;
        while (link != null) {
            Joint j = body.jointOf(link);
            if (j == null) {
                break;      // arrivati alla radice
            }
            for (Dof d : j.dofs()) {
                double w = Math.abs(d.axis.normalized().y());
                if (w > 0.3) {      // asse abbastanza verticale da far girare la testa di lato
                    yaw.add(d);
                    weights.add(w);
                }
            }
            String limb = traits.limbOfLink(link);
            String parent = body.link(link).parent;
            if (parent == null || !java.util.Objects.equals(limb, traits.limbOfLink(parent))) {
                break;      // il segmento successivo appartiene al tronco: il collo finisce qui
            }
            link = parent;
        }

        double range = 0;
        for (int i = 0; i < yaw.size(); i++) {
            Dof d = yaw.get(i);
            range += weights.get(i) * Math.min(Math.abs(d.lower), Math.abs(d.upper));
        }
        range = Math.min(MAX_YAW, range);

        List<YawDof> dofs = new ArrayList<>();
        if (range > 1e-3) {
            List<Dof> all = body.dofs();
            for (int i = 0; i < yaw.size(); i++) {
                Dof d = yaw.get(i);
                double limit = Math.min(Math.abs(d.lower), Math.abs(d.upper));
                double share = weights.get(i) * limit / range;
                int index = all.indexOf(d);
                if (index >= 0 && limit > 1e-4) {
                    dofs.add(new YawDof(index, share, limit));
                }
            }
        }
        return new Head(head, dofs.isEmpty() ? 0 : range, dofs);
    }

    /**
     * L'estremita' libera piu' alta nella posa di riposo, o la punta del tronco se non ce ne
     * sono: vedi la nota in testa alla classe.
     */
    private static String findHead(Automaton body, BodyTraits traits) {
        BodyPose rest = body.pose(null, V3.ZERO, Rot.IDENTITY);
        Set<String> support = new LinkedHashSet<>();
        for (Link l : body.links()) {
            String limb = traits.limbOfLink(l.name);
            if (limb != null && traits.supportLimbs().contains(limb)) {
                support.add(l.name);
            }
        }
        String best = null;
        double bestY = -Double.MAX_VALUE;
        for (String tip : body.extremities()) {
            if (support.contains(tip)) {
                continue;
            }
            BodyPose.LinkPose p = rest.get(tip);
            if (p == null) {
                continue;
            }
            double y = Math.max(p.coreA().y(), p.coreB().y());
            if (y > bestY) {
                bestY = y;
                best = tip;
            }
        }
        return best;
    }
}
