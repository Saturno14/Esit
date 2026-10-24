package src.body;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Posizione nello spazio mondo di tutti i link dell'automa per un certo vettore di angoli. */
public final class BodyPose {

    /** Pose di un link: origine (perno), orientamento e segmento interno della capsula con il suo raggio. */
    public record LinkPose(String name, V3 origin, Rot rot, V3 coreA, V3 coreB, double radius) {
        public V3 center() { return coreA.add(coreB).scale(0.5); }
    }

    private final Map<String, LinkPose> byName = new LinkedHashMap<>();

    BodyPose(List<LinkPose> poses) {
        for (LinkPose p : poses) {
            byName.put(p.name(), p);
        }
    }

    /**
     * La stessa posa spostata e girata: ogni punto diventa {@code r * (p - origine)}.
     *
     * Serve a portare nel mondo della simulazione la posa che arriva dal mondo fisico (vedi
     * JoltRagdoll.posaFisica): la ragdoll cammina sulla sua corsia, l'entita' sta da un'altra
     * parte e guarda da un'altra parte. Si sposta e si gira TUTTO IL CORPO insieme, cioe' non si
     * ricostruisce niente: quello che la fisica ha prodotto resta esattamente com'e', compresa
     * l'inclinazione, le gambe storte e il fatto che sia a terra.
     */
    public BodyPose transformed(Rot r, V3 origine) {
        List<LinkPose> out = new java.util.ArrayList<>();
        for (LinkPose p : byName.values()) {
            out.add(new LinkPose(p.name(),
                    r.apply(p.origin().sub(origine)), r.mul(p.rot()),
                    r.apply(p.coreA().sub(origine)), r.apply(p.coreB().sub(origine)), p.radius()));
        }
        return new BodyPose(out);
    }

    public LinkPose get(String link) { return byName.get(link); }
    public Iterable<LinkPose> links() { return byName.values(); }

    /** Quota minima raggiunta dalle capsule (superficie piu' bassa). */
    public double lowestPoint() {
        double min = Double.MAX_VALUE;
        for (LinkPose p : byName.values()) {
            min = Math.min(min, Math.min(p.coreA().y(), p.coreB().y()) - p.radius());
        }
        return min;
    }
}
