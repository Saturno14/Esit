package src.body;

import java.util.Set;

/**
 * Collisioni tra corpi articolati: capsula contro capsula (distanza tra segmenti meno i due raggi).
 * Pensato per il sistema di attacchi: invece di "cella adiacente" si chiede QUALE parte del corpo
 * dell'attaccante tocca QUALE parte del bersaglio. Il controllo a celle puo' restare come broadphase:
 * qui si fanno al massimo ~18x18 confronti, quindi va chiamato solo tra entita' vicine.
 */
public final class BodyCollision {

    /** gap < 0 = le capsule si compenetrano. */
    public record Contact(String attackerLink, String targetLink, double gap) {}

    private BodyCollision() {
    }

    public static double gap(BodyPose.LinkPose a, BodyPose.LinkPose b) {
        return segmentDistance(a.coreA(), a.coreB(), b.coreA(), b.coreB()) - a.radius() - b.radius();
    }

    /** Coppia di link (uno per corpo) piu' vicina, o null se uno dei due corpi e' vuoto. */
    public static Contact closest(BodyPose a, BodyPose b, Set<String> onlyFromA) {
        Contact best = null;
        for (BodyPose.LinkPose la : a.links()) {
            if (onlyFromA != null && !onlyFromA.contains(la.name())) continue;
            for (BodyPose.LinkPose lb : b.links()) {
                double g = gap(la, lb);
                if (best == null || g < best.gap()) best = new Contact(la.name(), lb.name(), g);
            }
        }
        return best;
    }

    /** Colpo andato a segno: una delle parti "colpitrici" (di solito {@link Automaton#extremities()}) dell'attaccante arriva a "reach" metri da una del bersaglio. */
    public static Contact strike(BodyPose attacker, java.util.Set<String> strikers, BodyPose target, double reach) {
        Contact c = closest(attacker, target, strikers);
        return c != null && c.gap() <= reach ? c : null;
    }

    public static boolean overlap(BodyPose a, BodyPose b) {
        Contact c = closest(a, b, null);
        return c != null && c.gap() < 0;
    }

    /** Distanza minima tra i segmenti p1-q1 e p2-q2 (Ericson, "Real-Time Collision Detection", 5.1.9). */
    public static double segmentDistance(V3 p1, V3 q1, V3 p2, V3 q2) {
        final double eps = 1e-12;
        V3 d1 = q1.sub(p1), d2 = q2.sub(p2), r = p1.sub(p2);
        double a = d1.dot(d1), e = d2.dot(d2), f = d2.dot(r);
        double s, t;
        if (a <= eps && e <= eps) {
            s = 0; t = 0;
        } else if (a <= eps) {
            s = 0; t = clamp(f / e);
        } else {
            double c = d1.dot(r);
            if (e <= eps) {
                t = 0; s = clamp(-c / a);
            } else {
                double b = d1.dot(d2), denom = a * e - b * b;
                s = denom > eps ? clamp((b * f - c * e) / denom) : 0;
                t = (b * s + f) / e;
                if (t < 0) { t = 0; s = clamp(-c / a); }
                else if (t > 1) { t = 1; s = clamp((b - c) / a); }
            }
        }
        return p1.add(d1.scale(s)).sub(p2.add(d2.scale(t))).length();
    }

    private static double clamp(double v) { return Math.min(1.0, Math.max(0.0, v)); }
}
