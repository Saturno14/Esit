package src.genetics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Piano del corpo: genoma della MORFOLOGIA, un albero di segmenti. Non e' fatto di alleli in [0,1]:
 * ogni segmento ha lunghezza, raggio, direzione, punto di attacco sul genitore e una lista di cerniere
 * (0 = giunto fisso, 1-3 = gradi di liberta'). Le mutazioni possono cambiare QUALSIASI cosa: valori,
 * posizione e orientamento dei giunti, tipo di giunto, e anche la struttura (aggiungere, togliere,
 * duplicare o spostare interi arti). Da un umanoide si puo' arrivare a un serpente (si tolgono arti e si
 * allunga una catena) o a un corpo con tre braccia (si duplica un braccio).
 *
 * Unita' SI, taglia ADULTA (la crescita scala tutto dopo). Convenzioni del corpo: X laterale (L = -X),
 * Y su, Z avanti. Tutti gli assi/direzioni sono nel frame del corpo a riposo.
 *
 * I soli limiti sono di sanita' numerica (LEN_MIN..LEN_MAX ecc.) e un tetto al numero di segmenti per
 * tenere sostenibile il costo della simulazione: non sono vincoli di design.
 *
 * Il piano e' immutabile dall'esterno; le operazioni di mutazione e crossover ne restituiscono uno nuovo.
 */
public final class BodyPlan {

    // limiti di sanita' numerica (non di design)
    public static final double LEN_MIN = 0.01, LEN_MAX = 12.0;
    public static final double RAD_MIN = 0.004, RAD_MAX = 3.0;
    public static final double ANGLE_LIMIT = 3.3;
    public static final int MAX_HINGES = 3;

    /** Una cerniera: asse (versore, frame del corpo a riposo) e limiti in radianti. */
    public record Hinge(double[] axis, double lower, double upper) {
        Hinge copy() { return new Hinge(axis.clone(), lower, upper); }
    }

    /** Frequenze e ampiezze delle mutazioni. */
    public record MutationRates(double pSegment, double sigma, double pAdd, double pRemove, double pDuplicate,
                                double pReparent, double pJoint, int maxSegments) {
        public static final MutationRates DEFAULT = new MutationRates(0.25, 0.08, 0.04, 0.04, 0.03, 0.02, 0.05, 64);
        public static final MutationRates NONE = new MutationRates(0, 0, 0, 0, 0, 0, 0, 64);
        /** Per i test: mutare molto e spesso. */
        public static final MutationRates WILD = new MutationRates(0.8, 0.25, 0.3, 0.25, 0.2, 0.15, 0.3, 40);
    }

    /** Un segmento rigido a forma di capsula con il suo giunto verso il genitore. */
    public static final class Seg {
        String id;                 // identificatore stabile = nome del link; stesso id in due genitori = stesso segmento ereditato
        String jointName;          // nome del giunto verso il genitore (null per la radice)
        String parent;             // null per la radice
        double length, radius;
        double[] dir;              // direzione di riposo, versore
        double attach;             // frazione [0,1] della lunghezza del genitore dove si attacca il perno
        double[] lateral;          // scostamento del perno rispetto al punto di attacco (metri, frame del corpo)
        double[] geom;             // scostamento della capsula rispetto al perno
        List<Hinge> hinges = new ArrayList<>();

        public String id() { return id; }
        public String jointName() { return jointName; }
        public String parent() { return parent; }
        public double length() { return length; }
        public double radius() { return radius; }
        public double[] dir() { return dir.clone(); }
        public double attach() { return attach; }
        public double[] lateral() { return lateral.clone(); }
        public double[] geom() { return geom.clone(); }
        public List<Hinge> hinges() { return java.util.Collections.unmodifiableList(hinges); }

        Seg copy() {
            Seg s = new Seg();
            s.id = id; s.jointName = jointName; s.parent = parent;
            s.length = length; s.radius = radius; s.dir = dir.clone(); s.attach = attach;
            s.lateral = lateral.clone(); s.geom = geom.clone();
            for (Hinge h : hinges) s.hinges.add(h.copy());
            return s;
        }
    }

    private final List<Seg> segs;           // ordine topologico: il genitore precede sempre i figli, la radice e' la prima

    // ------------------------------------------------------------------ costruzione

    private BodyPlan(List<Seg> list) {
        List<Seg> copy = new ArrayList<>();
        for (Seg s : list) {
            Seg c = s.copy();
            sanitize(c);
            copy.add(c);
        }
        this.segs = topological(copy);
        validate();
    }

    public List<Seg> segments() { return java.util.Collections.unmodifiableList(segs); }
    public int size() { return segs.size(); }
    public Seg root() { return segs.get(0); }

    public Seg get(String id) {
        for (Seg s : segs) if (s.id.equals(id)) return s;
        return null;
    }

    public boolean has(String id) { return get(id) != null; }

    /** Numero di segmenti che non hanno figli (estremita': mani, piedi, coda, testa...). */
    public int leafCount() {
        Set<String> parents = new HashSet<>();
        for (Seg s : segs) if (s.parent != null) parents.add(s.parent);
        return segs.size() - parents.size();
    }

    private Map<String, Seg> byId() {
        Map<String, Seg> m = new LinkedHashMap<>();
        for (Seg s : segs) m.put(s.id, s);
        return m;
    }

    private static void sanitize(Seg s) {
        s.length = clampFinite(s.length, LEN_MIN, LEN_MAX, 0.3);
        s.radius = clampFinite(s.radius, RAD_MIN, RAD_MAX, 0.05);
        s.dir = unit(s.dir, new double[]{0, 1, 0});
        s.attach = clampFinite(s.attach, 0.0, 1.0, 1.0);
        for (int i = 0; i < 3; i++) {
            s.lateral[i] = clampFinite(s.lateral[i], -LEN_MAX, LEN_MAX, 0);
            s.geom[i] = clampFinite(s.geom[i], -LEN_MAX, LEN_MAX, 0);
        }
        List<Hinge> fixed = new ArrayList<>();
        for (Hinge h : s.hinges) {
            if (fixed.size() >= MAX_HINGES) break;
            double lo = clampFinite(h.lower(), -ANGLE_LIMIT, ANGLE_LIMIT, -0.5);
            double hi = clampFinite(h.upper(), -ANGLE_LIMIT, ANGLE_LIMIT, 0.5);
            if (hi - lo < 0.05) {
                double mid = (lo + hi) / 2;
                lo = Math.max(-ANGLE_LIMIT, mid - 0.025);
                hi = Math.min(ANGLE_LIMIT, lo + 0.05);
            }
            fixed.add(new Hinge(unit(h.axis(), new double[]{1, 0, 0}), lo, hi));
        }
        s.hinges = fixed;
    }

    private void validate() {
        if (segs.isEmpty()) throw new IllegalArgumentException("piano del corpo vuoto");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < segs.size(); i++) {
            Seg s = segs.get(i);
            if (!ids.add(s.id)) throw new IllegalArgumentException("id duplicato: " + s.id);
            if (i == 0 && s.parent != null) throw new IllegalArgumentException("la prima voce deve essere la radice");
            if (i > 0 && (s.parent == null || !ids.contains(s.parent))) {
                throw new IllegalArgumentException("genitore mancante o successivo per " + s.id);
            }
            if (i > 0 && s.jointName == null) throw new IllegalArgumentException("giunto senza nome per " + s.id);
        }
    }

    /** Riordina in modo che il genitore preceda sempre i figli; scarta i segmenti irraggiungibili dalla radice. */
    private static List<Seg> topological(List<Seg> in) {
        Map<String, Seg> map = new LinkedHashMap<>();
        for (Seg s : in) map.put(s.id, s);
        Seg root = null;
        for (Seg s : in) if (s.parent == null) { root = s; break; }
        if (root == null) throw new IllegalArgumentException("nessuna radice");
        Map<String, List<Seg>> children = new HashMap<>();
        for (Seg s : in) if (s.parent != null) children.computeIfAbsent(s.parent, k -> new ArrayList<>()).add(s);
        List<Seg> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        java.util.ArrayDeque<Seg> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Seg s = stack.pop();
            if (!seen.add(s.id)) continue;
            out.add(s);
            List<Seg> ch = children.getOrDefault(s.id, List.of());
            for (int i = ch.size() - 1; i >= 0; i--) stack.push(ch.get(i));
        }
        return out;
    }

    // ------------------------------------------------------------------ piani di partenza

    /** Umanoide: lo stesso corpo di sempre (18 segmenti, 25 gradi di liberta'), dalle 8 proporzioni. */
    public static BodyPlan humanoid(double torso, double neck, double head, double limbLength,
                                    double limbThickness, double hand, double finger, double foot) {
        double[] up = {0, 1, 0}, down = {0, -1, 0}, fwd = {0, 0, 1};
        double t = limbThickness;
        double pelvisLen = torso * 0.15, pelvisR = t * 1.3, torsoR = t * 1.3;
        double upper = limbLength * 0.55, lower = limbLength * 0.45;
        List<Seg> l = new ArrayList<>();

        l.add(seg("pelvis", null, null, pelvisLen, pelvisR, up, 0, 0, 0, 0));
        l.add(seg("torso", "pelvis", "spine", torso, torsoR, up, 1.0, 0, 0, 0,
                h(1, 0, 0, -0.6, 0.6), h(0, 1, 0, -0.7, 0.7), h(0, 0, 1, -0.4, 0.4)));
        l.add(seg("neck", "torso", "neck", neck, t * 0.6, up, 1.0, 0, 0, 0,
                h(1, 0, 0, -0.9, 0.9), h(0, 1, 0, -1.2, 1.2)));
        l.add(seg("head", "neck", "skull_fix", head, head * 0.5, up, 1.0, 0, 0, 0));

        for (int side : new int[]{-1, 1}) {
            String s = side < 0 ? "L" : "R";
            double shoulderY = torso - Math.min(torsoR, torso * 0.2);
            l.add(seg("arm_upper_" + s, "torso", "shoulder_" + s, upper, t, down, shoulderY / torso,
                    side * (torsoR + t * 0.5), 0, 0,
                    h(1, 0, 0, -3.1, 1.0), sideZ(side, 2.8, 0.5), h(0, 1, 0, -1.2, 1.2)));
            l.add(seg("arm_lower_" + s, "arm_upper_" + s, "elbow_" + s, lower, t * 0.85, down, 1.0, 0, 0, 0,
                    h(1, 0, 0, -2.5, 0.0)));
            l.add(seg("hand_" + s, "arm_lower_" + s, "wrist_fix_" + s, hand, t * 0.6, down, 1.0, 0, 0, 0));
            l.add(seg("finger_" + s, "hand_" + s, "finger_fix_" + s, finger, t * 0.35, down, 1.0, 0, 0, 0));
        }
        for (int side : new int[]{-1, 1}) {
            String s = side < 0 ? "L" : "R";
            l.add(seg("leg_upper_" + s, "pelvis", "hip_" + s, upper, t * 1.2, down, 0.0,
                    side * pelvisR * 0.7, 0, 0,
                    h(1, 0, 0, -1.9, 0.6), sideZ(side, 0.8, 0.3), h(0, 1, 0, -0.6, 0.6)));
            l.add(seg("leg_lower_" + s, "leg_upper_" + s, "knee_" + s, lower, t, down, 1.0, 0, 0, 0,
                    h(1, 0, 0, 0.0, 2.5)));
            Seg foot_ = seg("foot_" + s, "leg_lower_" + s, "ankle_" + s, foot, t * 0.7, fwd, 1.0, 0, 0, 0,
                    h(1, 0, 0, -0.6, 0.9), h(0, 0, 1, -0.35, 0.35));
            foot_.geom = new double[]{0, 0, -0.25 * foot};   // il piede parte un quarto di piede dietro la caviglia (tallone)
            l.add(foot_);
        }
        return new BodyPlan(l);
    }

    /** Serpente: catena di "n" segmenti sdraiati lungo -Z, ognuno con imbardata e beccheggio. */
    public static BodyPlan snake(int n, double segLength, double radius) {
        double[] back = {0, 0, -1};
        List<Seg> l = new ArrayList<>();
        l.add(seg("body0", null, null, segLength, radius, back, 0, 0, 0, 0));
        for (int i = 1; i < Math.max(1, n); i++) {
            l.add(seg("body" + i, "body" + (i - 1), "bend" + i, segLength, radius * Math.pow(0.97, i), back, 1.0, 0, 0, 0,
                    h(0, 1, 0, -0.6, 0.6), h(1, 0, 0, -0.4, 0.4)));
        }
        return new BodyPlan(l);
    }

    private static Seg seg(String id, String parent, String joint, double length, double radius, double[] dir,
                           double attach, double lx, double ly, double lz, Hinge... hinges) {
        Seg s = new Seg();
        s.id = id; s.parent = parent; s.jointName = joint;
        s.length = length; s.radius = radius; s.dir = dir.clone(); s.attach = attach;
        s.lateral = new double[]{lx, ly, lz}; s.geom = new double[3];
        for (Hinge hg : hinges) s.hinges.add(hg);
        return s;
    }

    private static Hinge h(double x, double y, double z, double lo, double hi) {
        return new Hinge(new double[]{x, y, z}, lo, hi);
    }

    /** Abduzione attorno a Z con "out" verso l'esterno e "in" verso il piano mediano a seconda del lato. */
    private static Hinge sideZ(int side, double out, double in) {
        return h(0, 0, 1, side > 0 ? -in : -out, side > 0 ? out : in);
    }

    // ------------------------------------------------------------------ crossover

    /**
     * Crossover tra due corpi anche di forma diversa (stile NEAT): i segmenti con lo stesso id (cioe'
     * ereditati da un antenato comune) vengono fusi (lunghezza, raggio e attacco interpolati, il resto da
     * uno dei due); quelli presenti in un solo genitore passano con probabilita' 1/2, purche' il loro
     * genitore nel piano sopravviva.
     */
    public static BodyPlan cross(BodyPlan a, BodyPlan b, Random rng) {
        Map<String, Seg> ma = a.byId(), mb = b.byId();
        Map<String, Seg> chosen = new LinkedHashMap<>();
        Seg root = a.root().copy();
        chosen.put(root.id, root);
        List<String> ids = new ArrayList<>(ma.keySet());
        for (String id : mb.keySet()) if (!ma.containsKey(id)) ids.add(id);
        for (String id : ids) {
            if (id.equals(root.id)) continue;
            Seg sa = ma.get(id), sb = mb.get(id);
            Seg pick;
            if (sa != null && sb != null) {
                pick = (rng.nextBoolean() ? sa : sb).copy();
                double t = rng.nextDouble();
                pick.length = sa.length + (sb.length - sa.length) * t;
                pick.radius = sa.radius + (sb.radius - sa.radius) * t;
                pick.attach = sa.attach + (sb.attach - sa.attach) * t;
            } else {
                pick = (sa != null ? sa : sb).copy();
                if (rng.nextBoolean()) continue;
            }
            chosen.put(id, pick);
        }
        // un segmento sopravvive solo se la catena dei genitori arriva alla radice (senza cicli)
        List<Seg> out = new ArrayList<>();
        for (Seg s : chosen.values()) {
            if (reachesRoot(s, chosen)) out.add(s);
        }
        return new BodyPlan(out);
    }

    private static boolean reachesRoot(Seg s, Map<String, Seg> map) {
        Set<String> seen = new HashSet<>();
        Seg cur = s;
        while (cur.parent != null) {
            if (!seen.add(cur.id)) return false;
            cur = map.get(cur.parent);
            if (cur == null) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ mutazione

    public BodyPlan mutate(Random rng, MutationRates r) {
        List<Seg> list = new ArrayList<>();
        for (Seg s : segs) list.add(s.copy());

        // 1) variazione continua di ogni segmento (valori, punto di attacco, orientamento, limiti)
        for (Seg s : list) {
            if (rng.nextDouble() >= r.pSegment()) continue;
            if (rng.nextDouble() < 0.7) s.length *= Math.exp(rng.nextGaussian() * r.sigma());
            if (rng.nextDouble() < 0.7) s.radius *= Math.exp(rng.nextGaussian() * r.sigma());
            if (s.parent != null && rng.nextDouble() < 0.4) s.attach += rng.nextGaussian() * r.sigma() * 0.6;
            if (s.parent != null && rng.nextDouble() < 0.3) {
                for (int i = 0; i < 3; i++) s.lateral[i] += rng.nextGaussian() * r.sigma() * 0.25;
            }
            if (rng.nextDouble() < 0.4) rotateSegment(s, randomRotation(rng, r.sigma() * 1.8));
            for (int i = 0; i < s.hinges.size(); i++) {
                Hinge hg = s.hinges.get(i);
                double lo = hg.lower(), hi = hg.upper();
                double[] axis = hg.axis();
                if (rng.nextDouble() < 0.3) {
                    lo += rng.nextGaussian() * r.sigma() * 1.2;
                    hi += rng.nextGaussian() * r.sigma() * 1.2;
                }
                if (rng.nextDouble() < 0.2) axis = applyRotation(randomRotation(rng, r.sigma() * 1.8), axis);
                s.hinges.set(i, new Hinge(axis, lo, hi));
            }
        }

        // 2) mutazioni strutturali
        if (rng.nextDouble() < r.pAdd() && list.size() < r.maxSegments()) addSegment(list, rng);
        if (rng.nextDouble() < r.pRemove() && list.size() > 1) removeSubtree(list, rng);
        if (rng.nextDouble() < r.pDuplicate()) duplicateSubtree(list, rng, r.maxSegments());
        if (rng.nextDouble() < r.pReparent() && list.size() > 2) reparent(list, rng);
        if (rng.nextDouble() < r.pJoint() && list.size() > 1) changeJoint(list, rng);
        return new BodyPlan(list);
    }

    /**
     * Copia ESATTA del segmento "topId" con tutti i suoi discendenti (specchiata rispetto a X se "mirror"): nasce un
     * arto in piu'. Senza specchio la copia coincide con l'originale finche' una mutazione non la sposta.
     */
    public BodyPlan withDuplicatedSubtree(String topId, boolean mirror, Random rng) {
        List<Seg> list = copyOfSegs();
        Seg top = null;
        for (Seg s : list) if (s.id.equals(topId)) top = s;
        if (top == null || top.parent == null) throw new IllegalArgumentException("segmento non duplicabile: " + topId);
        duplicateSubtree(list, top, mirror, false, rng, Integer.MAX_VALUE);
        return new BodyPlan(list);
    }

    /** Toglie il segmento "topId" e tutto cio' che e' attaccato a valle. */
    public BodyPlan withoutSubtree(String topId) {
        List<Seg> list = copyOfSegs();
        if (get(topId) == null || get(topId).parent == null) throw new IllegalArgumentException("segmento non rimovibile: " + topId);
        removeSubtree(list, topId);
        return new BodyPlan(list);
    }

    private List<Seg> copyOfSegs() {
        List<Seg> list = new ArrayList<>();
        for (Seg s : segs) list.add(s.copy());
        return list;
    }

    private static void addSegment(List<Seg> list, Random rng) {
        Seg p = list.get(rng.nextInt(list.size()));
        boolean chain = rng.nextBoolean();                       // prolunga il genitore (catena) o ramo in direzione casuale
        double[] dir = chain ? p.dir.clone() : randomUnit(rng);
        Seg s = new Seg();
        s.id = newId(list, rng);
        s.parent = p.id;
        s.jointName = "j_" + s.id;
        s.length = p.length * Math.exp(rng.nextGaussian() * 0.4);
        s.radius = p.radius * (0.6 + 0.4 * rng.nextDouble());
        s.dir = dir;
        s.attach = chain ? 1.0 : rng.nextDouble();
        s.lateral = new double[3];
        s.geom = new double[3];
        double[][] basis = perpendicularBasis(dir);
        double[][] axes = {basis[0], basis[1], dir};
        double u = rng.nextDouble();
        int k = u < 0.1 ? 0 : u < 0.5 ? 1 : u < 0.9 ? 2 : 3;
        for (int i = 0; i < k; i++) {
            double span = i < 2 ? 0.4 + rng.nextDouble() : 0.2 + 0.6 * rng.nextDouble();
            s.hinges.add(new Hinge(axes[i].clone(), -span, span));
        }
        list.add(s);
    }

    private static void removeSubtree(List<Seg> list, Random rng) {
        List<Seg> cand = new ArrayList<>();
        for (Seg s : list) if (s.parent != null) cand.add(s);
        if (cand.isEmpty()) return;
        removeSubtree(list, cand.get(rng.nextInt(cand.size())).id);
    }

    private static void removeSubtree(List<Seg> list, String topId) {
        Set<String> gone = descendants(list, topId);
        list.removeIf(s -> gone.contains(s.id));
    }

    private static void duplicateSubtree(List<Seg> list, Random rng, int max) {
        List<Seg> cand = new ArrayList<>();
        for (Seg s : list) if (s.parent != null) cand.add(s);
        if (cand.isEmpty()) return;
        duplicateSubtree(list, cand.get(rng.nextInt(cand.size())), rng.nextBoolean(), true, rng, max);
    }

    private static void duplicateSubtree(List<Seg> list, Seg top, boolean mirror, boolean jitter, Random rng, int max) {
        Set<String> ids = descendants(list, top.id);
        if (list.size() + ids.size() > max) return;
        double[][] rot = randomRotation(rng, 0.5);
        Map<String, String> rename = new HashMap<>();
        for (Seg s : new ArrayList<>(list)) if (ids.contains(s.id)) rename.put(s.id, newId(list, rng, rename.values()));
        for (Seg s : new ArrayList<>(list)) {
            if (!ids.contains(s.id)) continue;
            Seg c = s.copy();
            c.id = rename.get(s.id);
            c.jointName = "j_" + c.id;
            if (s.id.equals(top.id)) {
                c.parent = top.parent;
                if (jitter) {
                    c.attach = s.attach + rng.nextGaussian() * 0.15;
                    if (!mirror) rotateSegment(c, rot);       // senza specchio, ruotato per non sovrapporsi all'originale
                }
            } else {
                c.parent = rename.get(s.parent);
            }
            if (mirror) mirrorX(c);
            list.add(c);
        }
    }

    private static void reparent(List<Seg> list, Random rng) {
        List<Seg> cand = new ArrayList<>();
        for (Seg s : list) if (s.parent != null) cand.add(s);
        Seg s = cand.get(rng.nextInt(cand.size()));
        Set<String> sub = descendants(list, s.id);
        List<Seg> targets = new ArrayList<>();
        for (Seg t : list) if (!sub.contains(t.id) && !t.id.equals(s.parent)) targets.add(t);
        if (targets.isEmpty()) return;
        s.parent = targets.get(rng.nextInt(targets.size())).id;
        s.attach = rng.nextDouble();
    }

    private static void changeJoint(List<Seg> list, Random rng) {
        List<Seg> cand = new ArrayList<>();
        for (Seg s : list) if (s.parent != null) cand.add(s);
        Seg s = cand.get(rng.nextInt(cand.size()));
        if (s.hinges.size() < MAX_HINGES && (s.hinges.isEmpty() || rng.nextBoolean())) {
            double span = 0.3 + rng.nextDouble();
            s.hinges.add(new Hinge(randomUnit(rng), -span, span));
        } else if (!s.hinges.isEmpty()) {
            s.hinges.remove(rng.nextInt(s.hinges.size()));
        }
    }

    private static Set<String> descendants(List<Seg> list, String rootId) {
        Set<String> out = new HashSet<>();
        out.add(rootId);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Seg s : list) if (s.parent != null && out.contains(s.parent) && out.add(s.id)) grew = true;
        }
        return out;
    }

    private static String newId(List<Seg> list, Random rng) { return newId(list, rng, List.of()); }

    private static String newId(List<Seg> list, Random rng, java.util.Collection<String> alsoTaken) {
        while (true) {
            String id = "x" + Integer.toHexString(rng.nextInt() & 0x7fffffff);
            boolean taken = alsoTaken.contains(id);
            for (Seg s : list) if (s.id.equals(id)) { taken = true; break; }
            if (!taken) return id;
        }
    }

    /** Riflessione rispetto al piano X = 0: una rotazione di asse a diventa una di asse -(M a) = (ax, -ay, -az). */
    private static void mirrorX(Seg s) {
        s.dir[0] = -s.dir[0];
        s.lateral[0] = -s.lateral[0];
        s.geom[0] = -s.geom[0];
        for (int i = 0; i < s.hinges.size(); i++) {
            Hinge hg = s.hinges.get(i);
            s.hinges.set(i, new Hinge(new double[]{hg.axis()[0], -hg.axis()[1], -hg.axis()[2]}, hg.lower(), hg.upper()));
        }
    }

    /** Ruota direzione e assi dei giunti dello stesso angolo, cosi' la geometria del giunto resta coerente. */
    private static void rotateSegment(Seg s, double[][] rot) {
        s.dir = applyRotation(rot, s.dir);
        s.geom = applyRotation(rot, s.geom);
        for (int i = 0; i < s.hinges.size(); i++) {
            Hinge hg = s.hinges.get(i);
            s.hinges.set(i, new Hinge(applyRotation(rot, hg.axis()), hg.lower(), hg.upper()));
        }
    }

    // ------------------------------------------------------------------ algebra minima su double[3]

    private static double[] unit(double[] v, double[] fallback) {
        if (v == null || v.length != 3) return fallback.clone();
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (!(l > 1e-9) || Double.isInfinite(l)) return fallback.clone();
        if (Math.abs(l - 1.0) < 1e-12) return v.clone();     // gia' unitario: niente rumore numerico (serve a rendere il JSON stabile)
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    private static double clampFinite(double v, double lo, double hi, double fallback) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return fallback;
        return Math.min(hi, Math.max(lo, v));
    }

    private static double[] randomUnit(Random rng) {
        return unit(new double[]{rng.nextGaussian(), rng.nextGaussian(), rng.nextGaussian()}, new double[]{0, 1, 0});
    }

    private static double[][] perpendicularBasis(double[] d) {
        double[] hv = Math.abs(d[0]) > 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double dot = hv[0] * d[0] + hv[1] * d[1] + hv[2] * d[2];
        double[] p1 = unit(new double[]{hv[0] - d[0] * dot, hv[1] - d[1] * dot, hv[2] - d[2] * dot}, new double[]{1, 0, 0});
        double[] p2 = {d[1] * p1[2] - d[2] * p1[1], d[2] * p1[0] - d[0] * p1[2], d[0] * p1[1] - d[1] * p1[0]};
        return new double[][]{p1, p2};
    }

    /** Matrice di rotazione 3x3 con asse casuale e angolo gaussiano di deviazione "sigma". */
    private static double[][] randomRotation(Random rng, double sigma) {
        double[] k = randomUnit(rng);
        double a = rng.nextGaussian() * sigma, c = Math.cos(a), s = Math.sin(a), t = 1 - c;
        return new double[][]{
                {t * k[0] * k[0] + c, t * k[0] * k[1] - s * k[2], t * k[0] * k[2] + s * k[1]},
                {t * k[0] * k[1] + s * k[2], t * k[1] * k[1] + c, t * k[1] * k[2] - s * k[0]},
                {t * k[0] * k[2] - s * k[1], t * k[1] * k[2] + s * k[0], t * k[2] * k[2] + c}};
    }

    private static double[] applyRotation(double[][] m, double[] v) {
        return new double[]{
                m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
                m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
                m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
    }

    // ------------------------------------------------------------------ JSON

    public JSONObject toJson() {
        JSONArray arr = new JSONArray();
        for (Seg s : segs) {
            JSONObject o = new JSONObject();
            o.put("id", s.id);
            o.put("joint", s.jointName == null ? JSONObject.NULL : s.jointName);
            o.put("parent", s.parent == null ? JSONObject.NULL : s.parent);
            o.put("length", s.length);
            o.put("radius", s.radius);
            o.put("dir", new JSONArray(s.dir));
            o.put("attach", s.attach);
            o.put("lateral", new JSONArray(s.lateral));
            o.put("geom", new JSONArray(s.geom));
            JSONArray hs = new JSONArray();
            for (Hinge hg : s.hinges) {
                JSONObject ho = new JSONObject();
                ho.put("axis", new JSONArray(hg.axis()));
                ho.put("lower", hg.lower());
                ho.put("upper", hg.upper());
                hs.put(ho);
            }
            o.put("hinges", hs);
            arr.put(o);
        }
        return new JSONObject().put("segments", arr);
    }

    public static BodyPlan fromJson(JSONObject obj) {
        List<Seg> list = new ArrayList<>();
        JSONArray arr = obj.getJSONArray("segments");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Seg s = new Seg();
            s.id = o.getString("id");
            s.jointName = o.isNull("joint") ? null : o.getString("joint");
            s.parent = o.isNull("parent") ? null : o.getString("parent");
            s.length = o.getDouble("length");
            s.radius = o.getDouble("radius");
            s.dir = vec(o.getJSONArray("dir"));
            s.attach = o.getDouble("attach");
            s.lateral = vec(o.getJSONArray("lateral"));
            s.geom = vec(o.getJSONArray("geom"));
            JSONArray hs = o.getJSONArray("hinges");
            for (int k = 0; k < hs.length(); k++) {
                JSONObject ho = hs.getJSONObject(k);
                s.hinges.add(new Hinge(vec(ho.getJSONArray("axis")), ho.getDouble("lower"), ho.getDouble("upper")));
            }
            list.add(s);
        }
        return new BodyPlan(list);
    }

    private static double[] vec(JSONArray a) { return new double[]{a.getDouble(0), a.getDouble(1), a.getDouble(2)}; }
}
