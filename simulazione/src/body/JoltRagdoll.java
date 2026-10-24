package src.body;

import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.CapsuleShapeSettings;
import com.github.stephengold.joltjni.CollisionGroup;
import com.github.stephengold.joltjni.HingeConstraint;
import com.github.stephengold.joltjni.HingeConstraintSettings;
import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.RotatedTranslatedShapeSettings;
import com.github.stephengold.joltjni.ShapeSettings;
import com.github.stephengold.joltjni.SixDofConstraint;
import com.github.stephengold.joltjni.SixDofConstraintSettings;
import com.github.stephengold.joltjni.SphereShapeSettings;
import com.github.stephengold.joltjni.SpringSettings;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.TwoBodyConstraintSettings;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import com.github.stephengold.joltjni.enumerate.ESpringMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Il corpo articolato di un'entita' ({@link Automaton}) dentro un mondo fisico Jolt: capsule
 * rigide tenute insieme da giunti con limiti e motori di posizione. E' il ponte tra la
 * morfologia (che muta liberamente, vedi BodyPlan) e la fisica vera.
 *
 * COME SI TRADUCE UN AUTOMA IN CORPI RIGIDI
 * - I link uniti da giunti FISSI (testa, mani, dita) non diventano corpi separati: si fondono nel
 *   corpo del genitore come forma composta. Meno corpi e nessun vincolo inutile da risolvere.
 *   Ogni gruppo cosi' ottenuto e' un "cluster".
 * - Giunto con 1 grado di liberta' (ginocchio, gomito, e la maggioranza dei giunti mutati):
 *   una cerniera Jolt, esatta.
 * - Giunto con 2-3 gradi di liberta' (spalla, anca, collo, caviglia): UN vincolo a sei gradi di
 *   liberta' con le traslazioni bloccate e gli assi di rotazione ortogonalizzati (Gram-Schmidt)
 *   a partire dagli assi del piano del corpo. Nell'umanoide gli assi sono gia' ortogonali, quindi
 *   non si perde nulla; dopo mutazioni che ruotano un singolo asse l'ortogonalizzazione e'
 *   un'APPROSSIMAZIONE (l'ordine di composizione delle rotazioni non e' lo stesso di
 *   Automaton.pose). La catena esatta di cerniere con link virtuali resta in BodyExport.urdf,
 *   per l'addestramento con simulatori esterni: qui si e' scelto di non creare corpi fittizi,
 *   perche' avrebbero falsato le masse (un corpo virtuale pesante cambia la dinamica dell'arto).
 * - Le parti della stessa entita' non si urtano tra loro (vedi PhysicsWorld.selfCollisionFilter).
 *
 * L'ordine dei gradi di liberta' e' quello di {@link Automaton#dofs()}: lo stesso contratto del
 * JSON e dell'URDF, cosi' un controllore vale per tutte le rappresentazioni.
 */
public final class JoltRagdoll {

    /** Attrito delle parti del corpo: serve per spingere contro il terreno senza slittare. */
    public static final float FRICTION = 0.9f;
    /** Molla dei motori di posizione: "muscolo" reattivo e criticamente smorzato. */
    /**
     * RIGIDEZZA DEI MOTORI, per newton-metro di coppia del giunto.
     *
     * Non e' un numero assoluto perche' non puo' esserlo: i corpi vanno da pochi chili a qualche
     * quintale, e la coppia del giunto (vedi Automaton.assignTorques) scala gia' con il carico che
     * deve reggere. Legando la rigidezza a quella, un corpo piccolo e uno enorme si comportano allo
     * stesso modo.
     *
     * PERCHE' NON LA FREQUENZA. Prima il motore era una molla a 20 Hz con smorzamento 1. Misurato:
     * un umanoide fermo nella posa eretta si accasciava comunque, con le anche che cedevano di 6
     * gradi dopo mezzo secondo, 36 dopo tre quarti, 56 dopo un secondo (i ginocchi, a cerniera,
     * tenevano entro 2). Alzare la frequenza non aiutava - a 200 Hz, oltre il limite di
     * campionamento del passo da 1/120 s, peggiorava - e nemmeno piu' iterazioni del solutore o un
     * passo di integrazione quattro volte piu' fine. Con la rigidezza esplicita il beccheggio dopo
     * un secondo passa da +0.61 a -0.01 rad e il corpo tiene la posa.
     */
    public static volatile double STIFFNESS_PER_TORQUE = 100;
    public static volatile double DAMPING_PER_TORQUE = 100;
    /** Usati solo se la rigidezza e' disattivata (<=0): la vecchia molla a frequenza. */
    public static volatile float MOTOR_FREQUENCY = 20f;
    public static volatile float MOTOR_DAMPING = 1f;
    /** Jolt accetta limiti di rotazione entro +-pi; il piano del corpo arriva a +-3.3 rad. */
    private static final float MAX_ANGLE = (float) Math.PI - 1e-3f;

    private static final V3 UNIT_Y = new V3(0, 1, 0);

    /** Un giunto motorizzato: una cerniera oppure un vincolo a tre assi di rotazione. */
    private record JointCtl(int firstDof, int dofCount, HingeConstraint hinge, SixDofConstraint six,
                            int parentId, int childId) {
    }

    public final Automaton automaton;
    private final PhysicsWorld world;

    private final List<String> clusterRoots = new ArrayList<>();
    private final List<List<Link>> clusterMembers = new ArrayList<>();
    private final List<Integer> clusterBodyIds = new ArrayList<>();
    private final List<Double> clusterMass = new ArrayList<>();
    private final Map<String, Integer> clusterOfLink = new LinkedHashMap<>();
    private final Map<String, V3> restOrigin = new LinkedHashMap<>();
    private final List<JointCtl> controls = new ArrayList<>();
    private final List<TwoBodyConstraint> constraints = new ArrayList<>();
    private final List<Body> bodies = new ArrayList<>();

    private final float[] target;
    private boolean removed = false;

    // ------------------------------------------------------------------ costruzione

    /**
     * Mette un corpo articolato nel mondo, in piedi sulla quota indicata.
     *
     * @param groundY quota del terreno: il punto piu' basso del corpo parte appena sopra
     * @param yaw     direzione verso cui guarda (radianti attorno a Y)
     * @param groupId identificatore dell'entita': le parti con lo stesso id non si urtano
     */
    public static JoltRagdoll spawn(PhysicsWorld world, Automaton automaton,
                                    double x, double groundY, double z, double yaw, int groupId) {
        return new JoltRagdoll(world, automaton, x, groundY, z, yaw, groupId);
    }

    private JoltRagdoll(PhysicsWorld world, Automaton automaton,
                        double x, double groundY, double z, double yaw, int groupId) {
        this.world = world;
        this.automaton = automaton;
        this.target = new float[automaton.dofCount()];

        computeRestOrigins();
        groupClusters();

        Rot rootRot = Rot.yaw(yaw);
        // il bacino va alzato di quanto serve perche' il punto piu' basso tocchi il terreno
        V3 rootPos = new V3(x, groundY + automaton.groundOffset() + 0.01, z);

        createBodies(rootPos, rootRot, groupId);
        createJoints(rootPos, rootRot);
    }

    /** Origine (perno) di ogni link nel frame del corpo a riposo: la radice e' nell'origine. */
    private void computeRestOrigins() {
        for (Link l : automaton.links()) {
            if (l.parent == null) {
                restOrigin.put(l.name, V3.ZERO);
            } else {
                Joint j = automaton.jointOf(l.name);
                restOrigin.put(l.name, restOrigin.get(l.parent).add(j.offset()));
            }
        }
    }

    /** Un cluster per ogni link il cui giunto verso il genitore NON e' fisso (piu' la radice). */
    private void groupClusters() {
        for (Link l : automaton.links()) {
            String root = clusterRootOf(l);
            Integer index = clusterOfLink.get(root);
            if (index == null) {
                index = clusterRoots.size();
                clusterRoots.add(root);
                clusterMembers.add(new ArrayList<>());
                clusterMass.add(0.0);
            }
            clusterOfLink.put(l.name, index);
            clusterMembers.get(index).add(l);
            clusterMass.set(index, clusterMass.get(index) + l.mass);
        }
    }

    private String clusterRootOf(Link l) {
        Link cur = l;
        while (cur.parent != null) {
            Joint j = automaton.jointOf(cur.name);
            if (j == null || !j.isFixed()) {
                break;
            }
            cur = automaton.link(cur.parent);
        }
        return cur.name;
    }

    private void createBodies(V3 rootPos, Rot rootRot, int groupId) {
        BodyInterface bi = world.bodies();
        for (int c = 0; c < clusterRoots.size(); c++) {
            String rootName = clusterRoots.get(c);
            V3 clusterOrigin = restOrigin.get(rootName);
            ShapeSettings shape = clusterShape(c, clusterOrigin);

            V3 worldOrigin = rootPos.add(rootRot.apply(clusterOrigin));
            Quat rot = quat(rootRot);
            BodyCreationSettings bcs = new BodyCreationSettings(shape,
                    new RVec3(worldOrigin.x(), worldOrigin.y(), worldOrigin.z()), rot,
                    EMotionType.Dynamic, PhysicsWorld.LAYER_BODY);
            // sottogruppo = indice del cluster: vedi PhysicsWorld.selfCollisionFilter()
            int subGroup = Math.min(c, PhysicsWorld.MAX_PARTS_PER_BODY - 1);
            bcs.setCollisionGroup(new CollisionGroup(world.selfCollisionFilter(), groupId, subGroup));
            bcs.setFriction(FRICTION);
            bcs.setLinearDamping(0.05f);
            bcs.setAngularDamping(0.1f);
            bcs.setAllowSleeping(true);

            Body body = bi.createBody(bcs);
            bi.addBody(body.getId(), EActivation.Activate);
            clusterBodyIds.add(body.getId());
            bodies.add(body);
        }
    }

    /**
     * Forma del cluster: le capsule dei suoi link, posizionate rispetto all'origine del link
     * radice (cosi' l'origine del corpo rigido coincide con il perno del suo giunto e i punti
     * dei vincoli sono immediati).
     */
    private ShapeSettings clusterShape(int cluster, V3 clusterOrigin) {
        List<Link> members = clusterMembers.get(cluster);
        if (members.size() == 1) {
            Link l = members.get(0);
            return placedCapsule(l, restOrigin.get(l.name).sub(clusterOrigin));
        }
        StaticCompoundShapeSettings compound = new StaticCompoundShapeSettings();
        for (Link l : members) {
            V3 offset = restOrigin.get(l.name).sub(clusterOrigin).add(l.center());
            compound.addShape(vec(offset), fromTo(UNIT_Y, l.dir), capsule(l));
        }
        return compound;
    }

    /** Capsula di un link, ruotata lungo la sua direzione e spostata al suo posto nel cluster. */
    private ShapeSettings placedCapsule(Link l, V3 linkOriginInCluster) {
        V3 offset = linkOriginInCluster.add(l.center());
        Quat rot = fromTo(UNIT_Y, l.dir);
        return new RotatedTranslatedShapeSettings(
                (float) offset.x(), (float) offset.y(), (float) offset.z(),
                rot.getX(), rot.getY(), rot.getZ(), rot.getW(), capsule(l));
    }

    /** Capsula centrata nell'origine e allungata su Y (come la vuole Jolt), densita' dell'automa. */
    private ShapeSettings capsule(Link l) {
        float radius = (float) l.radius;
        float halfHeight = (float) (l.cylinderHeight() / 2.0);
        if (halfHeight < 1e-4f) {
            SphereShapeSettings sphere = new SphereShapeSettings(radius);
            sphere.setDensity((float) Automaton.DENSITY);
            return sphere;
        }
        CapsuleShapeSettings settings = new CapsuleShapeSettings(halfHeight, radius);
        settings.setDensity((float) Automaton.DENSITY);
        return settings;
    }

    private void createJoints(V3 rootPos, Rot rootRot) {
        for (Link l : automaton.links()) {
            if (l.parent == null) {
                continue;
            }
            Joint j = automaton.jointOf(l.name);
            if (j.isFixed()) {
                continue;   // il link e' fuso nel cluster del genitore
            }
            Body parentBody = bodies.get(clusterOfLink.get(l.parent));
            Body childBody = bodies.get(clusterOfLink.get(l.name));
            // il perno del giunto e' esattamente l'origine del link figlio
            V3 pivot = rootPos.add(rootRot.apply(restOrigin.get(l.name)));
            int firstDof = automaton.dofIndex(j.name());
            List<Dof> dofs = j.dofs();

            if (dofs.size() == 1) {
                controls.add(new JointCtl(firstDof, 1,
                        hinge(parentBody, childBody, pivot, rootRot.apply(dofs.get(0).axis), dofs.get(0)),
                        null, parentBody.getId(), childBody.getId()));
            } else {
                controls.add(new JointCtl(firstDof, dofs.size(), null,
                        sixDof(parentBody, childBody, pivot, rootRot, dofs),
                        parentBody.getId(), childBody.getId()));
            }
        }
        applyTargets();
    }

    private HingeConstraint hinge(Body parent, Body child, V3 pivot, V3 axisWorld, Dof dof) {
        V3 axis = axisWorld.normalized();
        V3 normal = anyPerpendicular(axis);

        HingeConstraintSettings s = new HingeConstraintSettings();
        s.setSpace(EConstraintSpace.WorldSpace);
        s.setPoint1(pivot.x(), pivot.y(), pivot.z());
        s.setPoint2(pivot.x(), pivot.y(), pivot.z());
        s.setHingeAxis1((float) axis.x(), (float) axis.y(), (float) axis.z());
        s.setHingeAxis2((float) axis.x(), (float) axis.y(), (float) axis.z());
        // normale uguale sui due corpi: nella posa di riposo l'angolo del giunto vale 0
        s.setNormalAxis1((float) normal.x(), (float) normal.y(), (float) normal.z());
        s.setNormalAxis2((float) normal.x(), (float) normal.y(), (float) normal.z());
        s.setLimitsMin(limit(dof.lower));
        s.setLimitsMax(limit(dof.upper));
        motor(s.getMotorSettings(), dof.maxTorque);

        HingeConstraint c = (HingeConstraint) create(s, parent, child);
        c.setMotorState(EMotorState.Position);
        c.setTargetAngle(0f);
        return c;
    }

    private SixDofConstraint sixDof(Body parent, Body child, V3 pivot, Rot rootRot, List<Dof> dofs) {
        // assi ortogonalizzati: X = primo asse, Y = componente del secondo perpendicolare a X
        V3 ax = rootRot.apply(dofs.get(0).axis).normalized();
        V3 ay = rootRot.apply(dofs.get(1).axis);
        ay = ay.sub(ax.scale(ay.dot(ax)));
        ay = ay.length() < 1e-6 ? anyPerpendicular(ax) : ay.normalized();

        SixDofConstraintSettings s = new SixDofConstraintSettings();
        s.setSpace(EConstraintSpace.WorldSpace);
        s.setPosition1(pivot.x(), pivot.y(), pivot.z());
        s.setPosition2(pivot.x(), pivot.y(), pivot.z());
        s.setAxisX1((float) ax.x(), (float) ax.y(), (float) ax.z());
        s.setAxisX2((float) ax.x(), (float) ax.y(), (float) ax.z());
        s.setAxisY1((float) ay.x(), (float) ay.y(), (float) ay.z());
        s.setAxisY2((float) ay.x(), (float) ay.y(), (float) ay.z());

        s.makeFixedAxis(EAxis.TranslationX);
        s.makeFixedAxis(EAxis.TranslationY);
        s.makeFixedAxis(EAxis.TranslationZ);
        EAxis[] rotAxes = {EAxis.RotationX, EAxis.RotationY, EAxis.RotationZ};
        for (int k = 0; k < 3; k++) {
            if (k < dofs.size()) {
                Dof d = dofs.get(k);
                s.setLimitedAxis(rotAxes[k], limit(d.lower), limit(d.upper));
                MotorSettings ms = s.getMotorSettings(rotAxes[k]);
                motor(ms, d.maxTorque);
            } else {
                s.makeFixedAxis(rotAxes[k]);
            }
        }

        SixDofConstraint c = (SixDofConstraint) create(s, parent, child);
        for (int k = 0; k < dofs.size(); k++) {
            c.setMotorState(rotAxes[k], EMotorState.Position);
        }
        c.setTargetOrientationCs(new Quat(0, 0, 0, 1));
        return c;
    }

    private TwoBodyConstraint create(TwoBodyConstraintSettings settings, Body a, Body b) {
        TwoBodyConstraint c = settings.create(a, b);
        world.system().addConstraint(c);
        constraints.add(c);
        return c;
    }

    private static void motor(MotorSettings ms, double maxTorque) {
        ms.setTorqueLimit((float) maxTorque);
        SpringSettings spring = ms.getSpringSettings();
        if (STIFFNESS_PER_TORQUE > 0) {
            spring.setMode(ESpringMode.StiffnessAndDamping);
            spring.setStiffness((float) (STIFFNESS_PER_TORQUE * maxTorque));
            spring.setDamping((float) (DAMPING_PER_TORQUE * maxTorque));
        } else {
            spring.setMode(ESpringMode.FrequencyAndDamping);
            spring.setFrequency(MOTOR_FREQUENCY);
            spring.setDamping(MOTOR_DAMPING);
        }
    }

    private static float limit(double angle) {
        return (float) Math.max(-MAX_ANGLE, Math.min(MAX_ANGLE, angle));
    }

    // ------------------------------------------------------------------ controllo

    /**
     * Angoli obiettivo dei gradi di liberta' (ordine di {@link Automaton#dofs()}, radianti):
     * e' l'uscita del controllore dell'andatura. I motori ci arrivano con la loro molla, non di
     * colpo, e non superano la coppia massima del giunto.
     */
    public void setTargetAngles(float[] q) {
        int n = Math.min(q.length, target.length);
        for (int i = 0; i < n; i++) {
            target[i] = q[i];
        }
        applyTargets();
    }

    /** Come setTargetAngles ma per un solo grado di liberta'. */
    public void setTargetAngle(int dof, float angle) {
        if (dof < 0 || dof >= target.length) {
            return;
        }
        target[dof] = angle;
        applyTargets();
    }

    private void applyTargets() {
        List<Dof> dofs = automaton.dofs();
        for (JointCtl ctl : controls) {
            if (ctl.hinge() != null) {
                Dof d = dofs.get(ctl.firstDof());
                ctl.hinge().setTargetAngle(clamp(target[ctl.firstDof()], d));
            } else {
                float[] a = new float[3];
                for (int k = 0; k < ctl.dofCount() && k < 3; k++) {
                    Dof d = dofs.get(ctl.firstDof() + k);
                    a[k] = clamp(target[ctl.firstDof() + k], d);
                }
                ctl.six().setTargetOrientationCs(Quat.sEulerAngles(a[0], a[1], a[2]));
            }
        }
    }

    /** Gli assi di rotazione di un vincolo a sei gradi, nell'ordine dei gradi di liberta'. */
    private static final EAxis[] ROT_AXES = {EAxis.RotationX, EAxis.RotationY, EAxis.RotationZ};

    private static float clamp(float angle, Dof d) {
        return (float) Math.max(limit(d.lower), Math.min(limit(d.upper), angle));
    }

    /** Angoli effettivi dei giunti a 1 grado di liberta' (gli altri restano a 0: vedi javadoc). */
    /**
     * ATTENZIONE: legge solo i giunti a CERNIERA (un grado di liberta'); per i SixDof (giunti da
     * due o tre gradi) lascia zero, perche' Jolt non espone l'angolo per asse. Chi se ne fida per
     * capire se il corpo tiene la posa prende una risposta sbagliata - e' successo: sembrava che
     * tutti i motori tenessero mentre le anche cedevano di 56 gradi. Per quello si confronta la
     * posa vera, {@link #readPose()}, con quella comandata.
     */
    public float[] currentAngles() {
        float[] q = new float[target.length];
        for (JointCtl ctl : controls) {
            if (ctl.hinge() != null) {
                q[ctl.firstDof()] = ctl.hinge().getCurrentAngle();
            }
        }
        return q;
    }

    // ------------------------------------------------------------------ lettura dello stato

    /** Posizione del perno del link radice (il "bacino"). */
    public V3 rootPosition() {
        RVec3 p = world.bodies().getPosition(clusterBodyIds.get(0));
        return new V3(p.x(), p.y(), p.z());
    }

    /**
     * Orientamento del link radice (il "tronco"): dice da che parte sta cadendo il corpo. Lo
     * legge {@link MotorSense} come senso (beccheggio e rollio, vedi {@link Rot#tilt()}) e il
     * disegno per sapere come mettere il corpo nel mondo.
     */
    public Rot rootRotation() {
        Quat q = world.bodies().getRotation(clusterBodyIds.get(0));
        return Rot.quaternion(q.getX(), q.getY(), q.getZ(), q.getW());
    }

    /**
     * LA POSA VERA: dove sta ogni pezzo del corpo nel mondo fisico, adesso.
     *
     * Non ricostruisce niente. Prima il disegno prendeva da qui gli ANGOLI dei giunti e poi
     * rifaceva la posa con la cinematica diretta, riappoggiando i piedi a terra e girando tutto
     * verso la direzione in cui l'entita' guarda: era una ricreazione del movimento, e si vedeva
     * un corpo in piedi anche quando nel mondo fisico era a terra. Questo metodo legge invece la
     * trasformata di ogni corpo rigido come sta in Jolt, quindi quello che si disegna E' il corpo
     * fisico: se si trascina, si trascina; se cade in continuazione, cade in continuazione.
     *
     * COME: ogni cluster e' un corpo rigido la cui origine e' il perno del suo link radice (vedi
     * createBodies), e i link dentro un cluster sono fusi, quindi condividono la rotazione. Per
     * un link basta portare il suo scostamento a riposo dentro il cluster nel mondo con la
     * rotazione del cluster.
     */
    public BodyPose posaFisica() {
        int n = clusterCount();
        V3[] pos = new V3[n];
        Rot[] rot = new Rot[n];
        leggiTrasformate(pos, rot);
        return posaDa(pos, rot);
    }

    /** Quante parti rigide ha questo corpo (i cluster: vedi groupClusters). */
    public int clusterCount() {
        return clusterBodyIds.size();
    }

    /**
     * Il corpo rigido di Jolt che porta questo cluster.
     *
     * Serve a chi deve agire su un pezzo di corpo dall'esterno - oggi l'acqua, che spinge pezzo
     * per pezzo perche' un corpo lungo puo' avere le gambe immerse e il tronco fuori.
     */
    public int clusterBody(int c) {
        return clusterBodyIds.get(c);
    }

    /** Dove sta questo cluster adesso: serve a sapere se e' sotto il pelo dell'acqua. */
    public V3 clusterPosition(int c) {
        RVec3 p = world.bodies().getPosition(clusterBodyIds.get(c));
        return new V3(p.x(), p.y(), p.z());
    }

    /**
     * Copia fuori le trasformate delle parti rigide. Separato da {@link #posaDa} perche' LEGGERE
     * il mondo fisico si puo' fare solo dal thread della fisica, mentre comporre la posa e' puro
     * calcolo e lo puo' fare chiunque, anche il disegno (vedi MondoFisico e la sua istantanea).
     */
    public void leggiTrasformate(V3[] pos, Rot[] rot) {
        BodyInterface bi = world.bodies();
        int n = Math.min(clusterBodyIds.size(), Math.min(pos.length, rot.length));
        for (int c = 0; c < n; c++) {
            int id = clusterBodyIds.get(c);
            RVec3 p = bi.getPosition(id);
            pos[c] = new V3(p.x(), p.y(), p.z());
            rot[c] = rotationOf(bi, id);
        }
    }

    /**
     * La posa che corrisponde a queste trasformate delle parti rigide. Non tocca il mondo fisico:
     * usa solo la struttura del corpo, che non cambia piu' dopo la costruzione.
     */
    public BodyPose posaDa(V3[] pos, Rot[] rot) {
        List<BodyPose.LinkPose> out = new ArrayList<>();
        for (Link l : automaton.links()) {
            int c = clusterOfLink.get(l.name);
            V3 dentro = restOrigin.get(l.name).sub(restOrigin.get(clusterRoots.get(c)));
            V3 origin = pos[c].add(rot[c].apply(dentro));
            Rot r = rot[c];
            out.add(new BodyPose.LinkPose(l.name, origin, r,
                    origin.add(r.apply(l.coreA())), origin.add(r.apply(l.coreB())), l.radius));
        }
        return new BodyPose(out);
    }

    /**
     * Verso che parte e' girato il corpo attorno all'asse verticale, in radianti.
     *
     * Serve a portare la posa fisica nel mondo: l'imbardata e' l'unica rotazione che NON viene
     * dalla fisica, perche' e' la simulazione a possederla (il campo facing dell'entita', che
     * decide anche dove l'entita' va per davvero). Togliendo questa si lascia tutto il resto -
     * inclinazione, torsione, crollo - esattamente come la fisica l'ha prodotto.
     *
     * Si guarda l'asse "avanti" del tronco portato nel mondo e si prende la sua direzione
     * orizzontale. Se quel vettore e' quasi verticale (il corpo e' a faccia in giu' o a testa in
     * su) la sua direzione orizzontale non vuol dire niente, e allora si usa l'asse laterale: due
     * assi perpendicolari non possono essere verticali insieme.
     */
    public double yawRoot() {
        Rot r = rootRotation();
        V3 avanti = r.apply(new V3(0, 0, 1));
        V3 su = r.apply(new V3(0, 1, 0));
        /*
         * CONTINUA, SENZA RICADUTE. La versione precedente usava l'asse avanti e, quando questo
         * era quasi verticale, RICADEVA sull'asse laterale "riportato indietro di 90 gradi": ma
         * il verso di quei 90 gradi dipende da se il muso punta in giu' o in su, e la formula
         * non lo guardava. Risultato: un corpo che si piegava col muso in su vedeva la propria
         * direzione saltare di MEZZO GIRO esatto (misurato: -3.141 rad in MondoFisicoSelfTest),
         * e nel mondo unico la direzione dell'entita' - quindi il suo cono di vista - si
         * capovolgeva.
         *
         * Qui la direzione e' quella di avanti_orizzontale - avanti.y * su_orizzontale. Con il
         * muso a terra avanti si accorcia e l'asse "su" del tronco punta in avanti: lo si somma.
         * Con il muso in alto l'asse "su" punta indietro e avanti.y cambia segno: si somma di
         * nuovo nel verso giusto. Con il corpo dritto avanti.y e' zero e il termine sparisce.
         * Con il solo rollio (il corpo su un fianco) lo stesso. E' un vettore che non si annulla
         * finche' il tronco non e' verticale E rovesciato attorno alla verticale - dove "da che
         * parte guarda" non ha comunque un senso.
         */
        double x = avanti.x() - avanti.y() * su.x();
        double z = avanti.z() - avanti.y() * su.z();
        if (Math.hypot(x, z) < 1e-6) {
            V3 lato = r.apply(new V3(1, 0, 0));
            return Math.atan2(lato.x(), lato.z()) - Math.PI / 2;
        }
        return Math.atan2(x, z);
    }

    /**
     * Spegne o riaccende TUTTI i motori dei giunti.
     *
     * Spenti, il corpo e' una marionetta senza fili: i giunti restano attaccati e dentro i loro
     * limiti, ma niente li tiene, quindi il corpo cede sotto il suo peso. E' quello che serve a
     * un'entita' che DORME: non da' piu' comandi al corpo, e se cade e' normale. Non e' un modo
     * di far cadere i corpi - e' l'assenza di comandi, che e' una cosa diversa.
     */
    public void motoriSpenti(boolean spenti) {
        EMotorState stato = spenti ? EMotorState.Off : EMotorState.Position;
        for (JointCtl ctl : controls) {
            if (ctl.hinge() != null) {
                ctl.hinge().setMotorState(stato);
            } else {
                for (int k = 0; k < ctl.dofCount() && k < 3; k++) {
                    ctl.six().setMotorState(ROT_AXES[k], stato);
                }
            }
        }
        if (!spenti) {
            applyTargets();
        }
    }

    /** Baricentro di tutto il corpo, pesato sulle masse dei cluster. */
    public V3 centerOfMass() {
        double m = 0;
        V3 sum = V3.ZERO;
        for (int c = 0; c < clusterBodyIds.size(); c++) {
            RVec3 p = world.bodies().getCenterOfMassPosition(clusterBodyIds.get(c));
            double mass = clusterMass.get(c);
            sum = sum.add(new V3(p.x(), p.y(), p.z()).scale(mass));
            m += mass;
        }
        return m > 0 ? sum.scale(1.0 / m) : sum;
    }

    /** Quota del punto piu' basso delle capsule: serve per capire se il corpo e' caduto. */
    public double lowestPoint() {
        return readPose().lowestPoint();
    }

    /**
     * Posa di tutti i link cosi' come sono ADESSO nel mondo fisico: e' quello che serve al
     * motore grafico per disegnare il corpo mentre cammina, e a BodyCollision per sapere quale
     * parte tocca quale.
     */
    public BodyPose readPose() {
        BodyInterface bi = world.bodies();
        List<BodyPose.LinkPose> poses = new ArrayList<>();
        for (int c = 0; c < clusterBodyIds.size(); c++) {
            int id = clusterBodyIds.get(c);
            RVec3 p = bi.getPosition(id);
            Quat q = bi.getRotation(id);
            V3 origin = new V3(p.x(), p.y(), p.z());
            Rot rot = Rot.quaternion(q.getX(), q.getY(), q.getZ(), q.getW());
            V3 clusterOrigin = restOrigin.get(clusterRoots.get(c));
            for (Link l : clusterMembers.get(c)) {
                V3 local = restOrigin.get(l.name).sub(clusterOrigin);
                V3 linkOrigin = origin.add(rot.apply(local));
                poses.add(new BodyPose.LinkPose(l.name, linkOrigin, rot,
                        linkOrigin.add(rot.apply(l.coreA())), linkOrigin.add(rot.apply(l.coreB())), l.radius));
            }
        }
        return new BodyPose(poses);
    }

    // ------------------------------------------------------------------ propriocezione

    /**
     * ANGOLO VERO DI OGNI GRADO DI LIBERTA', giunti a due e tre assi compresi.
     *
     * Prima si leggeva solo la cerniera ({@link #currentAngles()}) e i giunti a piu' assi
     * restavano a zero, perche' Jolt non espone l'angolo per asse: era documentato come limite e
     * costava mezza giornata di caccia al difetto sbagliato (vedi il TODO). Adesso serve per
     * davvero, perche' la propriocezione e' un INGRESSO del controllore: un riflesso che non sa
     * dov'e' l'anca non puo' correggere l'anca.
     *
     * COME. Si prende la rotazione del figlio rispetto al genitore, la si porta negli assi del
     * genitore (che a riposo sono gli assi del corpo, cioe' quelli in cui sono scritti gli assi
     * dei gradi di liberta') e si proietta il suo vettore asse-angolo sull'asse di ogni grado di
     * liberta'. Per una cerniera resta esatto - e per quelle si usa comunque il valore di Jolt,
     * che e' esatto per definizione.
     */
    public float[] jointAngles(float[] out) {
        float[] q = out != null && out.length == target.length ? out : new float[target.length];
        java.util.Arrays.fill(q, 0f);
        List<Dof> dofs = automaton.dofs();
        BodyInterface bi = world.bodies();
        for (JointCtl ctl : controls) {
            if (ctl.hinge() != null) {
                q[ctl.firstDof()] = ctl.hinge().getCurrentAngle();
                continue;
            }
            Rot parent = rotationOf(bi, ctl.parentId());
            Rot child = rotationOf(bi, ctl.childId());
            V3 rel = parent.inverse().mul(child).rotationVector();
            for (int k = 0; k < ctl.dofCount(); k++) {
                int i = ctl.firstDof() + k;
                if (i < q.length) {
                    q[i] = (float) rel.dot(dofs.get(i).axis.normalized());
                }
            }
        }
        return q;
    }

    /**
     * VELOCITA' ANGOLARE DI OGNI GRADO DI LIBERTA' (rad/s): la velocita' angolare relativa tra i
     * due corpi del giunto, proiettata sull'asse. E' l'altra meta' della propriocezione - un
     * riflesso che sa solo dove sta l'arto e non quanto velocemente si muove arriva sempre tardi.
     */
    public float[] jointRates(float[] out) {
        float[] v = out != null && out.length == target.length ? out : new float[target.length];
        java.util.Arrays.fill(v, 0f);
        List<Dof> dofs = automaton.dofs();
        BodyInterface bi = world.bodies();
        for (JointCtl ctl : controls) {
            Vec3 wp = bi.getAngularVelocity(ctl.parentId());
            Vec3 wc = bi.getAngularVelocity(ctl.childId());
            V3 rel = new V3(wc.getX() - wp.getX(), wc.getY() - wp.getY(), wc.getZ() - wp.getZ());
            V3 local = rotationOf(bi, ctl.parentId()).inverse().apply(rel);
            for (int k = 0; k < ctl.dofCount(); k++) {
                int i = ctl.firstDof() + k;
                if (i < v.length) {
                    v[i] = (float) local.dot(dofs.get(i).axis.normalized());
                }
            }
        }
        return v;
    }

    private static Rot rotationOf(BodyInterface bi, int bodyId) {
        Quat q = bi.getRotation(bodyId);
        return Rot.quaternion(q.getX(), q.getY(), q.getZ(), q.getW());
    }

    /** Velocita' del baricentro (m/s), pesata sulle masse come {@link #centerOfMass()}. */
    public V3 comVelocity() {
        double m = 0;
        V3 sum = V3.ZERO;
        BodyInterface bi = world.bodies();
        for (int c = 0; c < clusterBodyIds.size(); c++) {
            Vec3 v = bi.getLinearVelocity(clusterBodyIds.get(c));
            double mass = clusterMass.get(c);
            sum = sum.add(new V3(v.getX(), v.getY(), v.getZ()).scale(mass));
            m += mass;
        }
        return m > 0 ? sum.scale(1.0 / m) : sum;
    }

    /** Velocita' angolare del tronco (rad/s): da qui vengono beccheggio e rollio in velocita'. */
    public V3 rootAngularVelocity() {
        Vec3 w = world.bodies().getAngularVelocity(clusterBodyIds.get(0));
        return new V3(w.getX(), w.getY(), w.getZ());
    }

    /**
     * QUALE SEGMENTO STA TOCCANDO TERRA, nell'ordine di {@link Automaton#links()}.
     *
     * Non passa dal motore di collisione: guarda se il punto piu' basso della capsula di quel
     * segmento e' entro {@code margine} dal pavimento. Nel mondo del palco e dell'oracolo l'unica
     * cosa che si puo' toccare E' il pavimento, quindi la risposta e' la stessa e non costa un
     * ascoltatore di contatti; il giorno in cui nel mondo fisico ci sara' anche il terreno vero,
     * questa e' la riga da cambiare.
     *
     * Scritto su un vettore fornito da chi chiama: viene letto decine di volte al secondo per
     * ogni corpo sul palco, e allocare qui sarebbe lavoro per il garbage collector a ogni passo.
     */
    public void readContacts(double groundY, double margine, boolean[] out) {
        BodyInterface bi = world.bodies();
        List<Link> links = automaton.links();
        int n = Math.min(out.length, links.size());
        java.util.Arrays.fill(out, 0, n, false);
        for (int c = 0; c < clusterBodyIds.size(); c++) {
            int id = clusterBodyIds.get(c);
            RVec3 pos = bi.getPosition(id);
            Rot rot = rotationOf(bi, id);
            V3 origin = new V3(pos.x(), pos.y(), pos.z());
            V3 clusterOrigin = restOrigin.get(clusterRoots.get(c));
            for (Link l : clusterMembers.get(c)) {
                int idx = links.indexOf(l);
                if (idx < 0 || idx >= n) {
                    continue;
                }
                V3 local = restOrigin.get(l.name).sub(clusterOrigin);
                V3 linkOrigin = origin.add(rot.apply(local));
                V3 a = linkOrigin.add(rot.apply(l.coreA()));
                V3 b = linkOrigin.add(rot.apply(l.coreB()));
                double piuBasso = Math.min(a.y(), b.y()) - l.radius;
                out[idx] = piuBasso <= groundY + margine;
            }
        }
    }

    /** Numero di corpi rigidi usati da questo corpo articolato (cluster). */
    public int rigidBodyCount() {
        return clusterBodyIds.size();
    }

    /** Numero di vincoli (giunti motorizzati). */
    public int constraintCount() {
        return constraints.size();
    }

    /** Toglie il corpo dal mondo fisico. */
    public void remove() {
        if (removed) {
            return;
        }
        removed = true;
        for (TwoBodyConstraint c : constraints) {
            world.system().removeConstraint(c);
        }
        BodyInterface bi = world.bodies();
        for (int id : clusterBodyIds) {
            bi.removeBody(id);
            bi.destroyBody(id);
        }
    }

    // ------------------------------------------------------------------ utilita'

    private static Vec3 vec(V3 v) {
        return new Vec3((float) v.x(), (float) v.y(), (float) v.z());
    }

    private static Quat quat(Rot r) {
        // traccia della matrice -> quaternione (metodo di Shepperd, ramo piu' stabile)
        double t = r.get(0, 0) + r.get(1, 1) + r.get(2, 2);
        double x, y, z, w;
        if (t > 0) {
            double s = Math.sqrt(t + 1.0) * 2;
            w = 0.25 * s;
            x = (r.get(2, 1) - r.get(1, 2)) / s;
            y = (r.get(0, 2) - r.get(2, 0)) / s;
            z = (r.get(1, 0) - r.get(0, 1)) / s;
        } else if (r.get(0, 0) > r.get(1, 1) && r.get(0, 0) > r.get(2, 2)) {
            double s = Math.sqrt(1.0 + r.get(0, 0) - r.get(1, 1) - r.get(2, 2)) * 2;
            w = (r.get(2, 1) - r.get(1, 2)) / s;
            x = 0.25 * s;
            y = (r.get(0, 1) + r.get(1, 0)) / s;
            z = (r.get(0, 2) + r.get(2, 0)) / s;
        } else if (r.get(1, 1) > r.get(2, 2)) {
            double s = Math.sqrt(1.0 + r.get(1, 1) - r.get(0, 0) - r.get(2, 2)) * 2;
            w = (r.get(0, 2) - r.get(2, 0)) / s;
            x = (r.get(0, 1) + r.get(1, 0)) / s;
            y = 0.25 * s;
            z = (r.get(1, 2) + r.get(2, 1)) / s;
        } else {
            double s = Math.sqrt(1.0 + r.get(2, 2) - r.get(0, 0) - r.get(1, 1)) * 2;
            w = (r.get(1, 0) - r.get(0, 1)) / s;
            x = (r.get(0, 2) + r.get(2, 0)) / s;
            y = (r.get(1, 2) + r.get(2, 1)) / s;
            z = 0.25 * s;
        }
        return new Quat((float) x, (float) y, (float) z, (float) w);
    }

    /** Quaternione che porta il versore "from" sul versore "to". */
    private static Quat fromTo(V3 from, V3 to) {
        return Quat.sFromTo(vec(from), vec(to));
    }

    private static V3 anyPerpendicular(V3 axis) {
        V3 helper = Math.abs(axis.x()) > 0.9 ? new V3(0, 1, 0) : new V3(1, 0, 0);
        V3 p = helper.sub(axis.scale(helper.dot(axis)));
        return p.length() < 1e-9 ? new V3(0, 0, 1) : p.normalized();
    }
}
