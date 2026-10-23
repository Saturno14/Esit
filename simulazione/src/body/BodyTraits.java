package src.body;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Quello che un corpo "vale" nella simulazione, ricavato dalla sua forma e non da geni separati:
 * massa, altezza, portata delle estremita', arti di appoggio e loro lunghezza.
 *
 * Serve a far contare la morfologia: la massa pesa su salute e costo del movimento, la lunghezza
 * degli arti di appoggio sulla falcata, la portata su attacco e interazioni. Tutto in unita' SI
 * e sulla taglia ATTUALE del corpo (la crescita e' gia' dentro l'Automaton).
 *
 * ARTI. Un "arto" e' il sottoalbero che parte dove il corpo si ramifica: un segmento inizia un
 * arto se il suo genitore ha piu' di un figlio (o se e' la radice). In un umanoide gli arti sono
 * bacino, torso, collo+testa, due braccia e due gambe; in un serpente c'e' un arto solo. Un arto
 * e' "di appoggio" se una sua parte, nella posa di riposo, arriva in basso quanto i piedi: sono
 * quelli che portano il peso e fanno la falcata.
 */
public final class BodyTraits {

    /** Quanto in basso deve arrivare una parte per contare come appoggio (frazione dell'altezza). */
    private static final double SUPPORT_BAND = 0.15;

    /**
     * Massa e portata di un umanoide adulto medio (geni body.* a meta' intervallo): sono i
     * riferimenti con cui si pesano gli effetti sulle statistiche, cosi' un corpo "normale" ha
     * fattore 1 e il bilanciamento misurato finora non si sposta.
     */
    public static final double REFERENCE_MASS = 115.0;
    public static final double REFERENCE_REACH = 1.22;

    private final Automaton body;
    private final double mass;
    private final double height;
    private final double reach;
    private final double legLength;
    private final double longestLimb;
    private final int extremityCount;
    private final Set<String> supportLimbs = new LinkedHashSet<>();
    private final Map<String, String> limbOfLink = new LinkedHashMap<>();
    private final Map<String, String> limbOfJoint = new LinkedHashMap<>();
    private final Map<String, Double> limbSide = new LinkedHashMap<>();
    private final Map<String, Double> limbLength = new LinkedHashMap<>();
    private final Map<String, Integer> depthOfJoint = new LinkedHashMap<>();
    private String trunkLimb;

    public static BodyTraits of(Automaton body) {
        return new BodyTraits(body);
    }

    private BodyTraits(Automaton body) {
        this.body = body;
        this.mass = body.totalMass();

        BodyPose rest = body.pose(null, V3.ZERO, Rot.IDENTITY);
        double lowest = rest.lowestPoint();
        double highest = -Double.MAX_VALUE;
        for (BodyPose.LinkPose p : rest.links()) {
            highest = Math.max(highest, Math.max(p.coreA().y(), p.coreB().y()) + p.radius());
        }
        this.height = Math.max(1e-3, highest - lowest);

        assignLimbs();
        measureLimbs(rest, lowest);

        // portata: quanto lontano dal baricentro arriva l'estremita' piu' distante
        V3 com = centerOfMass(rest);
        double maxReach = 0;
        Set<String> tips = body.extremities();
        for (String tip : tips) {
            BodyPose.LinkPose p = rest.get(tip);
            double d = Math.max(p.coreA().sub(com).length(), p.coreB().sub(com).length())
                    + body.link(tip).radius;
            maxReach = Math.max(maxReach, d);
        }
        this.reach = maxReach;
        this.extremityCount = tips.size();

        double legs = 0, longest = 0;
        for (Map.Entry<String, Double> e : limbLength.entrySet()) {
            longest = Math.max(longest, e.getValue());
            if (supportLimbs.contains(e.getKey())) {
                legs = Math.max(legs, e.getValue());
            }
        }
        this.legLength = legs;
        this.longestLimb = longest;
    }

    // ------------------------------------------------------------------ misure

    /** Massa totale del corpo (kg). */
    public double mass() {
        return mass;
    }

    /** Altezza da terra alla cima, in posa di riposo (m). */
    public double height() {
        return height;
    }

    /** Distanza massima tra il baricentro e un'estremita' (m): la portata di un colpo o di una presa. */
    public double reach() {
        return reach;
    }

    /** Lunghezza dell'arto di appoggio piu' lungo (m): la gamba. 0 se il corpo non ha appoggi. */
    public double legLength() {
        return legLength;
    }

    /** Lunghezza dell'arto piu' lungo, di appoggio o no (m). */
    public double longestLimb() {
        return longestLimb;
    }

    /** Quanti arti toccano terra: 1 = un solo appoggio, 2 = bipede, 4+ = pianta larga. */
    public int supportCount() {
        return supportLimbs.size();
    }

    /**
     * True se il corpo si appoggia sul TRONCO e non su arti: striscia. Non e' la stessa cosa di
     * avere un solo appoggio - un corpo puo' reggersi su un unico arto - e per muoversi serve
     * un'altra andatura (un'onda lungo il corpo, non una falcata). Serve distinguerli perche'
     * l'evoluzione, lasciata correre, ci arriva: su 488 corpi di 60 generazioni meta' finisce con
     * un solo appoggio.
     */
    public boolean crawls() {
        return supportLimbs.size() == 1 && supportLimbs.contains(trunkLimb);
    }

    /** L'arto della radice: il tronco. */
    public String trunkLimb() {
        return trunkLimb;
    }

    /** Numero di estremita' (parti senza figli): mani, piedi, teste, code. */
    public int extremityCount() {
        return extremityCount;
    }

    public Set<String> supportLimbs() {
        return java.util.Collections.unmodifiableSet(supportLimbs);
    }

    /** Arto a cui appartiene un segmento (null se sconosciuto). */
    public String limbOfLink(String link) {
        return limbOfLink.get(link);
    }

    /** Arto a cui appartiene un grado di liberta', dal nome del giunto (null se sconosciuto). */
    public String limbOfDof(String dofName) {
        for (Map.Entry<String, String> e : limbOfJoint.entrySet()) {
            if (dofName.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Quanto e' lontano dall'attacco dell'arto il giunto di questo grado di liberta': 0 per
     * l'anca, 1 per il ginocchio, 2 per la caviglia. Serve a dare all'andatura un ritardo che
     * scorre lungo l'arto (il ginocchio segue l'anca), che e' come si cammina davvero - e lungo
     * il tronco e' l'onda con cui si muove un corpo che striscia. -1 se sconosciuto.
     */
    public int depthOfDof(String dofName) {
        for (Map.Entry<String, Integer> e : depthOfJoint.entrySet()) {
            if (dofName.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return -1;
    }

    /** Lato dell'arto: negativo a sinistra (X < 0), positivo a destra, 0 al centro. */
    public double limbSide(String limb) {
        Double s = limbSide.get(limb);
        return s == null ? 0 : s;
    }

    public double limbLength(String limb) {
        Double l = limbLength.get(limb);
        return l == null ? 0 : l;
    }

    /**
     * Peso della massa sulle statistiche, compresso (radice cubica) e limitato: 1 per un corpo
     * medio, 0.6 per uno molto leggero, 2 per uno enorme. La compressione serve perche' le
     * mutazioni possono produrre corpi da pochi kg o da tonnellate, e un effetto lineare
     * renderebbe quei casi dominanti.
     */
    public double massFactor() {
        return clamp(Math.cbrt(mass / REFERENCE_MASS), 0.6, 2.0);
    }

    /** Peso della portata: 1 per braccia normali, fino a 2 per arti molto lunghi. */
    public double reachFactor() {
        return clamp(reach / REFERENCE_REACH, 0.4, 2.0);
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : lo;
    }

    /** Carico su ogni appoggio (kg): massa divisa per gli arti che la portano. */
    public double loadPerSupport() {
        return supportLimbs.isEmpty() ? mass : mass / supportLimbs.size();
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT,
                "massa %.1f kg, altezza %.2f m, portata %.2f m, gamba %.2f m, appoggi %d%s, estremita' %d",
                mass, height, reach, legLength, supportCount(), crawls() ? " (striscia)" : "", extremityCount);
    }

    // ------------------------------------------------------------------ costruzione interna

    private void assignLimbs() {
        Map<String, Integer> childCount = new LinkedHashMap<>();
        for (Link l : body.links()) {
            if (l.parent != null) {
                childCount.merge(l.parent, 1, Integer::sum);
            }
        }
        for (Link l : body.links()) {
            limbOfLink.put(l.name, limbRootOf(l, childCount));
        }
        for (Joint j : body.joints()) {
            limbOfJoint.put(j.name(), limbOfLink.get(j.child()));
            depthOfJoint.put(j.name(), depthFromLimbRoot(body.link(j.child())));
        }
    }

    /** Risale fino al segmento che apre l'arto: dove il corpo si ramifica, o la radice. */
    private String limbRootOf(Link l, Map<String, Integer> childCount) {
        Link cur = l;
        while (cur.parent != null) {
            int siblings = childCount.getOrDefault(cur.parent, 1);
            if (siblings > 1) {
                return cur.name;         // il genitore si ramifica: qui inizia un arto
            }
            cur = body.link(cur.parent);
        }
        return cur.name;
    }

    /** Quanti segmenti ci sono tra questo e l'inizio del suo arto. */
    private int depthFromLimbRoot(Link l) {
        String root = limbOfLink.get(l.name);
        int depth = 0;
        Link cur = l;
        while (cur.parent != null && !cur.name.equals(root) && depth < 64) {
            cur = body.link(cur.parent);
            depth++;
        }
        return depth;
    }

    private void measureLimbs(BodyPose rest, double lowest) {
        double supportThreshold = lowest + SUPPORT_BAND * height;
        Map<String, List<Link>> members = new LinkedHashMap<>();
        for (Link l : body.links()) {
            members.computeIfAbsent(limbOfLink.get(l.name), k -> new ArrayList<>()).add(l);
        }
        for (Map.Entry<String, List<Link>> e : members.entrySet()) {
            String limb = e.getKey();
            double length = 0, sideSum = 0;
            int n = 0;
            for (Link l : e.getValue()) {
                length += l.length;
                BodyPose.LinkPose p = rest.get(l.name);
                double bottom = Math.min(p.coreA().y(), p.coreB().y()) - p.radius();
                if (bottom <= supportThreshold) {
                    supportLimbs.add(limb);
                }
                sideSum += p.center().x();
                n++;
            }
            limbLength.put(limb, length);
            limbSide.put(limb, n > 0 ? sideSum / n : 0);
        }
        // il tronco (l'arto della radice) non e' una gamba: porta il peso ma non fa falcata.
        // Si toglie dagli appoggi solo se ci sono arti che reggono: se e' l'unico appoggio, il
        // corpo striscia davvero su di esso (vedi crawls()).
        trunkLimb = limbOfLink.get(body.links().get(0).name);
        if (supportLimbs.size() > 1) {
            supportLimbs.remove(trunkLimb);
        }
    }

    private V3 centerOfMass(BodyPose pose) {
        double m = 0;
        V3 sum = V3.ZERO;
        for (Link l : body.links()) {
            BodyPose.LinkPose p = pose.get(l.name);
            sum = sum.add(p.center().scale(l.mass));
            m += l.mass;
        }
        return m > 0 ? sum.scale(1.0 / m) : V3.ZERO;
    }
}
