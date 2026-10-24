package src.body;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable;
import com.github.stephengold.joltjni.GroupFilterTable;
import com.github.stephengold.joltjni.JobSystemThreadPool;
import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.ObjVsBpFilter;
import com.github.stephengold.joltjni.ObjVsObjFilter;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.TempAllocatorImpl;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;

/**
 * Un mondo fisico Jolt: strati di collisione, allocatore, pool di thread e il passo di
 * integrazione. E' il contenitore in cui vivono i corpi articolati delle entita'
 * ({@link JoltRagdoll}).
 *
 * Due strati di oggetti: LAYER_STATIC (terreno) e LAYER_BODY (parti delle entita'). Statico
 * contro statico non collide; tutto il resto si'. Le parti della STESSA entita' non si urtano
 * tra loro: non si fa con gli strati (sono pochi) ma con il filtro di gruppo di ogni corpo
 * (vedi JoltRagdoll).
 *
 * Unita' SI, come il resto di src.body: metri, kg, secondi, radianti.
 */
public final class PhysicsWorld implements AutoCloseable {

    public static final int LAYER_STATIC = 0;
    public static final int LAYER_BODY = 1;
    /**
     * Corpi di servizio dei giunti a piu' gradi di liberta' (vedi JoltRagdoll): esistono solo
     * per tenere in serie le cerniere, quindi non devono urtare NIENTE.
     */
    public static final int LAYER_GHOST = 2;
    private static final int OBJ_LAYERS = 3;
    private static final int BP_LAYERS = 2;

    /**
     * Massimo numero di parti (cluster) di un corpo: e' il tetto di segmenti di BodyPlan
     * (MutationRates.maxSegments), e dimensiona la tabella dell'autocollisione.
     */
    public static final int MAX_PARTS_PER_BODY = 64;

    /** Passo fisso di integrazione: 120 Hz da' giunti stabili con i motori di posizione. */
    public static final float TIME_STEP = 1f / 120f;

    private final PhysicsSystem system;
    private final TempAllocatorImpl allocator;
    private final JobSystemThreadPool jobs;
    private final GroupFilterTable selfFilter;
    private boolean closed = false;

    /**
     * @param maxBodies numero massimo di corpi rigidi (parti, non entita'): va dimensionato
     *                  sul peggiore dei casi, Jolt lo prealloca
     * @param threads   thread per la fisica (0 = tutti i core disponibili meno uno)
     */
    public PhysicsWorld(int maxBodies, int threads) {
        PhysicsNatives.require();

        BroadPhaseLayerInterfaceTable layerMap = new BroadPhaseLayerInterfaceTable(OBJ_LAYERS, BP_LAYERS)
                .mapObjectToBroadPhaseLayer(LAYER_STATIC, 0)
                .mapObjectToBroadPhaseLayer(LAYER_BODY, 1)
                .mapObjectToBroadPhaseLayer(LAYER_GHOST, 1);
        // I corpi di servizio escono subito dalla broadphase: niente coppie da esaminare.
        ObjVsBpFilter objVsBp = new ObjVsBpFilter(OBJ_LAYERS, BP_LAYERS)
                .disablePair(LAYER_GHOST, 0)
                .disablePair(LAYER_GHOST, 1);
        ObjVsObjFilter objVsObj = new ObjVsObjFilter(OBJ_LAYERS)
                .disablePair(LAYER_STATIC, LAYER_STATIC)
                .disablePair(LAYER_GHOST, LAYER_STATIC)
                .disablePair(LAYER_GHOST, LAYER_BODY)
                .disablePair(LAYER_GHOST, LAYER_GHOST);

        this.system = new PhysicsSystem();
        // numBodyMutexes 0 = scelta automatica di Jolt
        this.system.init(maxBodies, 0, 4 * maxBodies, maxBodies, layerMap, objVsBp, objVsObj);
        this.system.setGravity(0f, -(float) Automaton.GRAVITY, 0f);

        this.allocator = new TempAllocatorImpl(32 * 1024 * 1024);
        int n = threads > 0 ? threads : Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.jobs = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, n);
        applySolverSettings();

        // Una tabella sola, condivisa: ogni parte di un'entita' ha il suo sottogruppo e tutte le
        // coppie sono spente, quindi due parti con lo stesso id entita' non si urtano mai.
        this.selfFilter = new GroupFilterTable(MAX_PARTS_PER_BODY);
        /*
         * SOLO LE COPPIE i<j, e non e' una scelta: Jolt rifiuta disableCollision(i, i) con
         * "subgroups must be distinct". Quindi due parti con lo STESSO sottogruppo si urtano
         * sempre, e due entita' non possono condividere un gruppo per attraversarsi - provato il
         * 24/10/2026 per tenere il pavimento del palco piccolo, non si puo' fare. Dentro un corpo
         * non cambia niente, perche' un sottogruppo non si ripete mai.
         */
        for (int i = 0; i < MAX_PARTS_PER_BODY; i++) {
            for (int j = i + 1; j < MAX_PARTS_PER_BODY; j++) {
                this.selfFilter.disableCollision(i, j);
            }
        }
        this.selfFilter.setEmbedded();
    }

    /**
     * Filtro di gruppo condiviso che spegne le collisioni fra le parti della STESSA entita':
     * ogni parte riceve CollisionGroup(questo filtro, idEntita', 0) e il filtro dice che il
     * sottogruppo 0 non collide con se stesso. Entita' con id diverso si urtano normalmente.
     *
     * Serve perche' a riposo le parti si toccano (braccia lungo i fianchi) e un arto duplicato
     * da una mutazione puo' nascere esattamente sopra l'originale: con l'autocollisione attiva
     * il corpo esploderebbe al primo passo.
     */
    public GroupFilterTable selfCollisionFilter() {
        return selfFilter;
    }

    public PhysicsSystem system() {
        return system;
    }

    public BodyInterface bodies() {
        return system.getBodyInterface();
    }

    /**
     * Pavimento: una grossa scatola statica con la faccia superiore a y = "top". Per la
     * locomozione su terreno piatto basta questo; il terreno vero (mappa delle altezze) si
     * aggiunge quando la fisica entra nel mondo della simulazione.
     */
    public int addGround(float halfSize, float top, float friction) {
        float halfThickness = Math.max(1f, halfSize * 0.05f);
        BoxShape shape = new BoxShape(halfSize, halfThickness, halfSize);
        BodyCreationSettings bcs = new BodyCreationSettings(shape,
                new RVec3(0, top - halfThickness, 0), new Quat(0, 0, 0, 1),
                EMotionType.Static, LAYER_STATIC);
        bcs.setFriction(friction);
        return bodies().createAndAddBody(bcs, EActivation.DontActivate);
    }

    /** Da chiamare dopo aver creato molti corpi e prima di cominciare a integrare. */
    public void optimize() {
        system.optimizeBroadPhase();
    }

    /**
     * Iterazioni del solutore. I valori predefiniti di Jolt (10 e 2) sono anche quelli usati qui:
     * alzarli a 30/8 e 60/16 NON ha cambiato nulla sulla tenuta dei giunti (misurato il
     * 23/10/2026), quindi non c'e' motivo di pagarli. La cedevolezza era nel motore, non nel
     * solutore: vedi JoltRagdoll.STIFFNESS_PER_TORQUE.
     */
    private void applySolverSettings() {
        com.github.stephengold.joltjni.PhysicsSettings ps = system.getPhysicsSettings();
        ps.setNumVelocitySteps(VELOCITY_STEPS);
        ps.setNumPositionSteps(POSITION_STEPS);
        system.setPhysicsSettings(ps);
    }

    private static final int VELOCITY_STEPS = 10;
    private static final int POSITION_STEPS = 2;

    /** Avanza la fisica di un passo fisso (TIME_STEP). */
    public void step() {
        step(TIME_STEP, 1);
    }

    public void step(float dt, int collisionSteps) {
        system.update(dt, collisionSteps, allocator, jobs);
    }

    /** Numero di corpi rigidi attualmente nel mondo (parti, non entita'). */
    public int bodyCount() {
        return system.getNumBodies();
    }

    public int activeBodyCount() {
        return system.getNumActiveBodies(com.github.stephengold.joltjni.enumerate.EBodyType.RigidBody);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        // I nativi di Jolt vengono liberati dai finalizzatori di jolt-jni; qui si lascia solo
        // andare il riferimento al sistema, senza distruggere la factory globale (puo' servire
        // a un altro mondo nello stesso processo).
    }
}
