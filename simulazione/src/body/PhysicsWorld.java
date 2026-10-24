package src.body;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BodyInterface;
import com.github.stephengold.joltjni.Body;
import com.github.stephengold.joltjni.BodyLockWrite;
import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.CylinderShape;
import com.github.stephengold.joltjni.HeightFieldShapeSettings;
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
import com.github.stephengold.joltjni.Vec3;
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
     * IL TERRENO VERO: un campo di altezze statico, costruito dalla mappa delle altezze del mondo.
     *
     * E' quello che fa la differenza fra "il corpo cammina" e "il corpo cammina QUI": su un
     * pavimento piatto un'andatura e' una proprieta' del corpo, su un terreno e' una proprieta'
     * del corpo E del posto - se c'e' una buca si inciampa, se c'e' un dosso si prova a salirlo e
     * si puo' non riuscirci.
     *
     * COME LO VUOLE JOLT. I campioni sono ai NODI di una griglia quadrata (non al centro delle
     * celle), in ordine riga per riga, e il loro numero per lato deve essere un multiplo del
     * blocco (2 per difetto). La mappa del mondo e' dim x dim celle di un metro, quindi servono
     * dim+1 nodi per coprirle tutte, e si arrotonda per eccesso a un multiplo di 4 ripetendo il
     * bordo: il terreno sporge di un paio di metri oltre la mappa, che e' meglio di un corpo che
     * cade nel vuoto all'ultima cella.
     *
     * NaN in un campione vorrebbe dire "qui c'e' un buco nel terreno" per Jolt. Non lo si usa:
     * un buco vero nel mondo e' una colonna bassa, non un pavimento assente, e una ragdoll che
     * cade fuori dal mondo non e' una cosa che si vuole dover gestire.
     *
     * L'ORDINE DEI CAMPIONI E' UNA TRAPPOLA, e va usato {@link #indiceCampione}: Jolt indicizza
     * il campione del nodo (x, z) come {@code iz * lato + ix}, cioe' la Z scorre sulle RIGHE.
     * Scambiarli non da' nessun errore - da' il terreno TRASPOSTO, cioe' un mondo in cui le
     * collline sono dove la simulazione vede le valli, e nessuna prova se ne accorgerebbe se non
     * guardando da che parte rotola un corpo. (Verificato dal PhysicsSmokeTest il 25/10/2026.)
     *
     * @param altezze  altezze ai nodi, lato x lato (vedi {@link #indiceCampione})
     * @param lato     quanti nodi per lato
     * @param passo    distanza fra due nodi (m)
     * @param friction attrito del terreno (come il pavimento piatto)
     */
    public int addHeightField(float[] altezze, int lato, float passo, float friction) {
        if (altezze.length != lato * lato) {
            throw new IllegalArgumentException(
                    "campioni " + altezze.length + " per un lato di " + lato);
        }
        HeightFieldShapeSettings hfs = new HeightFieldShapeSettings(altezze,
                new Vec3(0, 0, 0), new Vec3(passo, 1, passo), lato);
        BodyCreationSettings bcs = new BodyCreationSettings(hfs,
                new RVec3(0, 0, 0), new Quat(0, 0, 0, 1), EMotionType.Static, LAYER_STATIC);
        bcs.setFriction(friction);
        return bodies().createAndAddBody(bcs, EActivation.DontActivate);
    }

    /**
     * Dove sta, nell'array dei campioni, l'altezza del nodo (ix, iz). Vedi la trappola
     * nell'intestazione di {@link #addHeightField}.
     */
    public static int indiceCampione(int ix, int iz, int lato) {
        return iz * lato + ix;
    }

    /**
     * UN OSTACOLO SOLIDO E FERMO: un cilindro statico. Oggi sono i tronchi degli alberi.
     *
     * Un albero era un divieto - entity.walk rifiutava il passo se la cella aveva un tronco - e un
     * divieto e' una regola di comportamento scritta nel mondo. Adesso e' un pezzo di legno: ci si
     * sbatte contro, ci si gira attorno, e un corpo che non sa girare ci resta davanti. Il raggio
     * e' quello con cui l'albero viene DISEGNATO (src.Flora.Tree.trunkRadius), altrimenti le
     * entita' sbatterebbero contro l'aria o passerebbero attraverso il legno.
     */
    public int addStaticCylinder(double x, double y, double z, float raggio, float mezzaAltezza,
                                 float friction) {
        CylinderShape shape = new CylinderShape(Math.max(0.01f, mezzaAltezza), Math.max(0.01f, raggio));
        BodyCreationSettings bcs = new BodyCreationSettings(shape,
                new RVec3(x, y, z), new Quat(0, 0, 0, 1), EMotionType.Static, LAYER_STATIC);
        bcs.setFriction(friction);
        return bodies().createAndAddBody(bcs, EActivation.DontActivate);
    }

    /**
     * Quanti nodi per lato servono per una mappa di {@code celle} celle: celle+1 arrotondato per
     * eccesso a un multiplo di 4 (Jolt vuole un multiplo del blocco, e 4 va bene per qualunque
     * blocco fino a 4).
     */
    public static int latoCampioni(int celle) {
        int n = celle + 1;
        return ((n + 3) / 4) * 4;
    }

    /**
     * Pavimento: una grossa scatola statica con la faccia superiore a y = "top".
     *
     * Resta per l'ORACOLO e per le prove: lì il terreno non deve esistere, perche' la misura di
     * un'andatura e' quanto quel corpo cammina, non quanto cammina su quella collina. Nel mondo
     * della simulazione il pavimento e' {@link #addHeightField}.
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

    /**
     * L'ACQUA: la spinta e la resistenza che un fluido fa su un corpo immerso, per un passo.
     *
     * Da chiamare, per il corpo che si trova in una colonna allagata, PRIMA del passo di fisica.
     * Non e' una zona d'acqua permanente nel mondo fisico: l'acqua della simulazione sta per
     * colonne (world.isWaterColumn) e cambia col terreno, quindi chi chiama decide a ogni passo
     * chi e' in acqua e a che quota sta il pelo. Il peso del calcolo sta dentro Jolt, che sa
     * quanta parte di ogni capsula e' sotto il pelo.
     *
     * LA SPINTA E' 1.0 E NON E' UNA SCELTA: in Jolt 1 vuol dire "densita' uguale a quella del
     * fluido", e i corpi di questo progetto hanno DENSITA' 1000 kg/m^3 (Automaton.DENSITY), cioe'
     * esattamente quella dell'acqua. Quindi un corpo immerso non affonda e non galleggia: resta.
     * Se un giorno la densita' diventasse un gene, questo numero diventerebbe il rapporto fra le
     * due densita' - e sarebbe un gene che decide se sai nuotare.
     *
     * Le due resistenze (lineare e angolare) sono i valori predefiniti di Jolt: non sono tarati
     * su niente, e non c'e' ancora una misura che dica quanto contano.
     *
     * @param bodyId     il corpo (un cluster di una ragdoll)
     * @param quotaPelo  quota del pelo dell'acqua (m)
     * @param dt         durata del passo (s)
     * @return true se Jolt ha trovato il corpo e ha applicato qualcosa
     */
    public boolean galleggia(int bodyId, double quotaPelo, float dt) {
        BodyLockWrite lock = new BodyLockWrite(system.getBodyLockInterfaceNoLock(), bodyId);
        try {
            if (!lock.succeededAndIsInBroadPhase()) {
                return false;
            }
            Body b = lock.getBody();
            return b.applyBuoyancyImpulse(
                    new RVec3(0, quotaPelo, 0),       // un punto qualunque del pelo dell'acqua
                    new Vec3(0, 1, 0),                // il pelo e' orizzontale
                    1.0f,                             // spinta: vedi sopra, non e' una manopola
                    DRAG_LINEARE, DRAG_ANGOLARE,
                    new Vec3(0, 0, 0),                // l'acqua e' ferma: non ci sono correnti
                    new Vec3(0, (float) -Automaton.GRAVITY, 0),
                    dt);
        } finally {
            lock.releaseLock();
        }
    }

    /** Resistenza lineare dell'acqua: valore predefinito di Jolt, non tarato. */
    public static final float DRAG_LINEARE = 0.5f;
    /** Resistenza angolare dell'acqua: valore predefinito di Jolt, non tarato. */
    public static final float DRAG_ANGOLARE = 0.01f;

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
