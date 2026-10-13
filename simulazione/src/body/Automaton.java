package src.body;

import src.genetics.BodyPlan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Corpo articolato di un'entita' (un "automa"): albero di link rigidi a capsula collegati da giunti
 * con limiti. Sostituisce il vecchio BoneRig puramente visivo come fonte di verita' del corpo.
 *
 * Il corpo deriva da un {@link BodyPlan} (albero di segmenti completamente mutabile), non da proporzioni fisse.
 *
 * Convenzioni (identiche al vecchio BoneRig): asse Y verso l'alto, Z in avanti, X laterale con il
 * lato "L" a X negativo. Unita' SI (metri, kg, N*m, rad). Tutti i frame dei link sono allineati agli
 * assi del corpo a riposo (angoli = 0): in questa posa le braccia pendono lungo i fianchi, a
 * differenza del rig visivo precedente che le teneva aperte.
 *
 * I link sono in ordine topologico (il genitore precede sempre il figlio) e joints.get(i) collega il
 * link i+1 al suo genitore. L'ordine dei gradi di liberta' ({@link #dofs()}) e' il CONTRATTO con la
 * policy di locomozione: non va cambiato senza riaddestrare.
 */
public final class Automaton {

    public static final double DENSITY = 1000.0;      // kg/m^3 (corpo ~ acqua)
    public static final double GRAVITY = 9.81;

    private static final double TORQUE_FACTOR = 2.5;  // coppia max = fattore * peso del sottoalbero * braccio
    private static final double TORQUE_MIN = 2.0;
    private static final double MAX_VELOCITY = 12.0;  // rad/s

    /** Piano di segmenti da cui deriva questo corpo e fattore di crescita applicato. */
    public final BodyPlan plan;
    public final double growth;
    private final List<Link> links = new ArrayList<>();
    private final List<Joint> joints = new ArrayList<>();
    private final Map<String, Link> linkByName = new HashMap<>();
    private final Map<String, Joint> jointByChild = new HashMap<>();
    private final List<Dof> allDofs = new ArrayList<>();
    private final Map<String, Integer> firstDof = new HashMap<>();

    private Automaton(BodyPlan plan, double growth) {
        this.plan = plan;
        this.growth = growth;
    }

    // ------------------------------------------------------------------ costruzione

    /** Umanoide classico dalle 8 proporzioni (equivale a fromPlan(BodyPlan.humanoid(...), crescita)). */
    public static Automaton humanoid(BodyParams p) {
        return fromPlan(BodyPlan.humanoid(p.torso(), p.neck(), p.head(), p.limbLength(), p.limbThickness(),
                p.hand(), p.finger(), p.foot()), p.growth());
    }

    /**
     * Costruisce il corpo da QUALSIASI piano di segmenti (umanoide, serpente, tre braccia...). "growth"
     * scala tutte le misure del piano (taglia adulta = 1).
     */
    public static Automaton fromPlan(BodyPlan plan, double growth) {
        Automaton a = new Automaton(plan, growth);
        for (BodyPlan.Seg s : plan.segments()) {
            double len = s.length() * growth, rad = s.radius() * growth;
            V3 dir = v(s.dir()), geom = v(s.geom()).scale(growth);
            if (s.parent() == null) {
                a.addRoot(s.id(), dir, len, rad, geom);
                continue;
            }
            Link parent = a.linkByName.get(s.parent());
            V3 offset = parent.dir.scale(parent.length * s.attach()).add(v(s.lateral()).scale(growth));
            java.util.Set<String> used = new java.util.HashSet<>();
            List<BodyPlan.Hinge> hinges = s.hinges();
            Dof[] dofs = new Dof[hinges.size()];
            for (int k = 0; k < dofs.length; k++) {
                BodyPlan.Hinge h = hinges.get(k);
                String letter = axisLetter(h.axis());
                if (letter == null || !used.add(letter)) {
                    letter = "a" + k;
                    used.add(letter);
                }
                dofs[k] = new Dof(s.jointName() + "_" + letter, v(h.axis()), h.lower(), h.upper(), MAX_VELOCITY);
            }
            a.attach(s.jointName(), s.parent(), s.id(), dir, len, rad, geom, offset, dofs);
        }
        a.assignTorques();
        return a;
    }

    private static V3 v(double[] d) { return new V3(d[0], d[1], d[2]); }

    /** "x", "y" o "z" se l'asse coincide con un asse del corpo (in qualunque verso), altrimenti null. */
    private static String axisLetter(double[] a) {
        double e = 1e-9;
        if (Math.abs(Math.abs(a[0]) - 1) < e && Math.abs(a[1]) < e && Math.abs(a[2]) < e) return "x";
        if (Math.abs(a[0]) < e && Math.abs(Math.abs(a[1]) - 1) < e && Math.abs(a[2]) < e) return "y";
        if (Math.abs(a[0]) < e && Math.abs(a[1]) < e && Math.abs(Math.abs(a[2]) - 1) < e) return "z";
        return null;
    }

    private void addRoot(String name, V3 dir, double length, double radius, V3 geomOffset) {
        Link l = new Link(name, null, dir, length, radius, geomOffset, DENSITY);
        links.add(l);
        linkByName.put(name, l);
    }

    private void attach(String jointName, String parent, String child, V3 dir, double length, double radius,
                        V3 geomOffset, V3 offset, Dof... dofs) {
        Link l = new Link(child, parent, dir, length, radius, geomOffset, DENSITY);
        links.add(l);
        linkByName.put(child, l);
        Joint j = new Joint(jointName, parent, child, offset, List.of(dofs));
        joints.add(j);
        jointByChild.put(child, j);
        firstDof.put(jointName, allDofs.size());
        allDofs.addAll(j.dofs());
    }

    /**
     * Coppia massima euristica: fattore * massa del sottoalbero a valle del giunto * g * distanza del suo
     * baricentro dal perno, in posa di riposo. Serve solo come punto di partenza per l'addestramento.
     */
    private void assignTorques() {
        BodyPose rest = pose(null, V3.ZERO, Rot.IDENTITY);
        for (Joint j : joints) {
            if (j.isFixed()) continue;
            java.util.Set<String> subtree = new java.util.HashSet<>();
            subtree.add(j.child());
            double m = 0;
            V3 com = V3.ZERO;
            for (Link l : links) {
                if (l.parent != null && subtree.contains(l.parent)) subtree.add(l.name);
                if (!subtree.contains(l.name)) continue;
                BodyPose.LinkPose lp = rest.get(l.name);
                m += l.mass;
                com = com.add(lp.center().scale(l.mass));
            }
            com = com.scale(1.0 / m);
            double reach = Math.max(com.sub(rest.get(j.child()).origin()).length(), 0.05);
            double torque = Math.max(TORQUE_MIN, TORQUE_FACTOR * m * GRAVITY * reach);
            for (Dof d : j.dofs()) d.maxTorque = torque;
        }
    }

    // ------------------------------------------------------------------ accesso

    public List<Link> links() { return links; }
    public List<Joint> joints() { return joints; }
    public Link link(String name) { return linkByName.get(name); }
    public Joint jointOf(String childLink) { return jointByChild.get(childLink); }
    /** Gradi di liberta' nell'ordine del vettore degli angoli / delle azioni della policy. */
    public List<Dof> dofs() { return allDofs; }
    public int dofCount() { return allDofs.size(); }
    /** Indice nel vettore degli angoli del primo grado di liberta' del giunto indicato. */
    public int dofIndex(String joint) { return firstDof.get(joint); }

    /** Nomi dei link senza figli (mani, piedi, testa, coda...): le parti che possono colpire. */
    public java.util.Set<String> extremities() {
        java.util.Set<String> parents = new java.util.HashSet<>();
        for (Link l : links) if (l.parent != null) parents.add(l.parent);
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (Link l : links) if (!parents.contains(l.name)) out.add(l.name);
        return out;
    }

    public double totalMass() {
        double m = 0;
        for (Link l : links) m += l.mass;
        return m;
    }

    public double[] clampToLimits(double[] q) {
        double[] r = new double[q.length];
        for (int i = 0; i < q.length; i++) {
            Dof d = allDofs.get(i);
            r[i] = Math.min(d.upper, Math.max(d.lower, q[i]));
        }
        return r;
    }

    /** Altezza a cui mettere il bacino perche' il punto piu' basso del corpo, a riposo, tocchi y = 0. */
    public double groundOffset() {
        return -pose(null, V3.ZERO, Rot.IDENTITY).lowestPoint();
    }

    // ------------------------------------------------------------------ cinematica diretta

    /**
     * @param q       angoli dei gradi di liberta' (radianti, ordine di {@link #dofs()}); null = tutti a zero
     * @param rootPos posizione mondo dell'origine del bacino
     * @param rootRot orientamento mondo del bacino
     */
    public BodyPose pose(double[] q, V3 rootPos, Rot rootRot) {
        if (q != null && q.length != allDofs.size()) {
            throw new IllegalArgumentException("attesi " + allDofs.size() + " angoli, ricevuti " + q.length);
        }
        Map<String, V3> origins = new HashMap<>();
        Map<String, Rot> rots = new HashMap<>();
        List<BodyPose.LinkPose> out = new ArrayList<>();

        for (Link l : links) {
            V3 origin;
            Rot rot;
            if (l.parent == null) {
                origin = rootPos;
                rot = rootRot;
            } else {
                Joint j = jointByChild.get(l.name);
                origin = origins.get(l.parent).add(rots.get(l.parent).apply(j.offset()));
                rot = rots.get(l.parent);
                int idx = firstDof.get(j.name());
                for (int k = 0; k < j.dofs().size(); k++) {
                    double angle = q == null ? 0.0 : q[idx + k];
                    rot = rot.mul(Rot.axisAngle(j.dofs().get(k).axis, angle));
                }
            }
            origins.put(l.name, origin);
            rots.put(l.name, rot);
            out.add(new BodyPose.LinkPose(l.name, origin, rot,
                    origin.add(rot.apply(l.coreA())), origin.add(rot.apply(l.coreB())), l.radius));
        }
        return new BodyPose(out);
    }
}
