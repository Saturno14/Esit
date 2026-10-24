package src;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONObject;

import src.brain.NeuralNetwork;
import src.brain.Reproduction;
import src.genetics.DietType;
import src.genetics.EntityStats;
import src.genetics.Genome;
import src.genetics.GenomeSchema;


/**
 * Una entita' della simulazione. Non ha piu' un thread proprio: il ciclo centrale
 * (Simulation) chiama advance() a ogni tick e l'entita' compie un'azione ogni volta che il
 * suo accumulatore di punti azione (legato al gene speed) arriva a 1.
 *
 * Azioni scelte dalla rete neurale (uscite), tutte EGOCENTRICHE: ferma, avanti, indietro, gira a
 * destra, gira a sinistra, testa a destra, testa a sinistra, mangia, attacca, dormi, riproduci,
 * bevi. Ogni azione (tranne ferma, dormi e girare la testa) consuma stamina; la stamina si
 * rigenera solo dormendo. Le caratteristiche vitali derivano dal genoma (EntityStats).
 *
 * PERCHE' EGOCENTRICHE. Prima le mosse erano i quattro punti cardinali e la vista era a settori
 * assoluti: la stessa scena (una mela davanti al muso) produceva ingressi diversi a seconda di
 * come l'entita' era girata, e la rete doveva imparare quattro volte la stessa cosa. Ora la vista
 * e' un cono centrato dove l'entita' guarda (vedi Vision) e le azioni sono riferite al muso,
 * quindi "cibo davanti, vai avanti" si impara una volta sola. In piu' l'entita' puo' girare la
 * TESTA senza girare il corpo, entro i limiti del suo collo, che sono genetici (vedi
 * src.body.Gaze): un collo flessibile fa guardare intorno, uno rigido no.
 */
public class entity {

    // --- Rete neurale: input e output FISSI per tutte le entita' ---
    /**
     * Ingressi sul proprio stato: salute, cibo, sete, stamina, x, z, eta', sesso, piu' i quattro
     * della PROPRIOCEZIONE DELLO SGUARDO - seno e coseno di quanto la testa e' girata rispetto al
     * corpo, e quanto collo resta da girare nei due versi. Senza questi l'entita' vedrebbe un
     * cono senza sapere dove e' puntato, cioe' non potrebbe collegare quello che vede a dove si
     * deve muovere.
     *
     * L'ultimo e' la LUCE DEL GIORNO. Serve perche' il gene della visione notturna possa essere
     * selezionato: se l'entita' non sa che e' buio non puo' cambiare comportamento col buio, e un
     * gene su cui il comportamento non ha presa non viene premiato ne' punito - e' solo rumore.
     */
    public static final int SELF_INPUTS = 14;
    public static final int INPUT_SIZE = Vision.SIZE + SELF_INPUTS;
    public static final int OUTPUT_SIZE = 12;
    public static final int[] BRAIN_TOPOLOGY = {INPUT_SIZE, 32, 16, OUTPUT_SIZE};

    public static final int ACT_IDLE = 0;
    public static final int ACT_FORWARD = 1;
    public static final int ACT_BACK = 2;
    public static final int ACT_TURN_RIGHT = 3;
    public static final int ACT_TURN_LEFT = 4;
    public static final int ACT_HEAD_RIGHT = 5;
    public static final int ACT_HEAD_LEFT = 6;
    public static final int ACT_EAT = 7;
    public static final int ACT_ATTACK = 8;
    public static final int ACT_SLEEP = 9;
    public static final int ACT_REPRODUCE = 10;
    public static final int ACT_DRINK = 11;

    /** Di quanto gira il corpo in un'azione (radianti). */
    private static final double TURN_STEP = Math.PI / 8;
    /** Di quanto gira la testa in un'azione (radianti): meno del corpo, e' un aggiustamento. */
    private static final double HEAD_STEP = Math.PI / 12;
    /**
     * Girare la testa non costa stamina: e' un muscolo del collo. Il costo e' un altro, e c'e' -
     * un'azione spesa a guardare e' un'azione non spesa a mangiare, e con il metabolismo legato
     * al tempo (vedi metabolism) il tempo si paga.
     */
    private static final double TURN_COST = 0.5;

    // --- Bilanciamento ---
    /** Riserva di cibo standard; e' anche il riferimento con cui si normalizza l'input "food" della rete. */
    public static final int MAX_FOOD = 200;
    /** Riserva massima in uso: alzabile per le simulazioni di addestramento (comando console train/maxfood). */
    private static volatile int maxFood = MAX_FOOD;

    public static int getMaxFood(){ return maxFood; }
    public static void setMaxFood(int value){ maxFood = Math.max(1, value); }
    public static final int MAX_THIRST = 200;
    public static final int MATURITY_AGE = 100;          // passi di vita prima di potersi riprodurre
    private static final double SPEED_DIVISOR = 7.0;     // speed medio (~1.75) => 1 azione ogni 4 tick
    /** Lato di una cella del mondo, in metri (come GridToWorld.CELL_SIZE nel motore grafico). */
    public static final double CELL_METERS = 1.0;
    /** Durata in secondi di un tick di mondo. */
    private static final double TICK_SECONDS = Simulation.TICK_MS / 1000.0;
    private static final double EXPLORATION = 0.15;      // probabilita' di azione casuale

    private static final double MOVE_COST = 2;
    private static final double EAT_COST = 1;
    private static final double ATTACK_COST = 6;
    private static final double REPRODUCE_COST = 8;
    private static final double SLEEP_REGEN_FRACTION = 0.15; // della stamina massima per passo di sonno
    /**
     * Stamina recuperata da svegli, per tick, in frazione della propria stamina massima.
     *
     * PERCHE' ESISTE. Prima la stamina si recuperava SOLO dormendo. Il risultato non era "chi non
     * dorme si stanca", era peggio: un passo costa ~2.5 e un giro del corpo 0.5, quindi appena la
     * stamina scendeva sotto il costo di un passo la maschera delle azioni possibili cancellava
     * il passo e lasciava in piedi le rotazioni - e l'entita' restava intrappolata a girarsi sul
     * posto, non per scelta ma perche' era l'unica cosa che potesse pagare. Nel primo mondo con
     * gli alberi erano 20000 rotazioni su 27000 azioni.
     *
     * La frazione e' SULLA PROPRIA stamina massima, quindi il gene stat.max_stamina decide anche
     * quanto velocemente si recupera: con 50 di stamina si recuperano 1.0 a tick contro i ~0.6 a
     * tick che costa camminare senza sosta, con 15 si recupera 0.3 e camminare in continuazione
     * non e' sostenibile. E' una pressione selettiva vera su quel gene, che prima non aveva.
     */
    private static final double WAKE_REGEN_FRACTION = 0.02;

    private static final int REPRO_FOOD_MIN = 80;
    private static final int REPRO_PARTNER_FOOD_MIN = 60;
    private static final int REPRO_FOOD_COST = 40;
    private static final int REPRO_COOLDOWN = 40;
    /**
     * Quanto dura una richiesta di accoppiamento, in tick. Deve bastare all'altra per GIRARE LA
     * TESTA e accorgersene: con una vista a cono, una richiesta che scade subito non si vede mai.
     */
    private static final int RICHIESTA_TTL = 30;
    /** Costo in stamina di una richiesta: chiedere non e' gratis, ma costa meno che fare un figlio. */
    private static final double RICHIESTA_COST = 2;
    private static final double BITE_MEAT = 60;
    private static final int PARTNER_RANGE = 3;          // celle entro cui un partner puo' essere raggiunto

    private static final int AGE_DRIFT_INTERVAL = 50;
    private static final double AGE_DRIFT_REFERENCE = 500.0;

    private static final int WINDOW_SIZE = 10;           // ogni quanti passi valuti la mutazione del cervello
    private static final double MUTATION_RATE = 0.25;
    /** Quanto muta l'andatura alla nascita (vedi src.body.Gait.perturb). */
    private static final double GAIT_MUTATION = 0.08;

    // Schema condiviso da tutte le entita' (caricato una volta sola dal classpath).
    private static final GenomeSchema SCHEMA = GenomeSchema.loadFromClasspath("/gene_schema.json");

    private int EntityID;
    private int healt;
    private int food;
    private int thirst;
    private double stamina;
    private int born;
    private int age;
    private int sex; // 1 maschio, 2 femmina
    private int consumedFood = 0;
    private int applesEaten = 0;
    private int kills = 0;
    private int reproCooldown = 0;
    /**
     * LA DIETA E' UN'ABITUDINE, NON UN'ETICHETTA. Da 0 (tutto vegetale) a 1 (tutta carne).
     * Parte dalla predisposizione del gene (stats.dietBias) e si sposta a ogni pasto verso cio'
     * che si e' appena mangiato; piu' e' sbilanciata verso un cibo, piu' quel cibo sazia (vedi
     * resa()). Nessuno vieta niente a nessuno: un erbivoro la carne la puo' mangiare, solo che
     * gli rende poco, e se insiste smette di essere un erbivoro.
     *
     * E' il motivo per cui la specializzazione diventa una TRAPPOLA, che e' la parte buona: chi
     * ha mangiato solo carne per cinquanta pasti ricava un quarto da una mela, quindi se i
     * cadaveri finiscono muore di fame in mezzo al cibo. Al gene della dieta serviva un prezzo,
     * e questo e' un prezzo - non un divieto.
     *
     * NON SI EREDITA: il figlio riparte dal gene dei genitori (vedi Genome.reproduce). Quello che
     * si tramanda e' il gusto di partenza, non l'abitudine acquisita.
     */
    private double dietBias;
    /** Chi mi ha chiesto di accoppiarsi, e quando: -1 = nessuno. Vedi ActionWeights.mateRequest. */
    private volatile int richiestaDa = -1;
    private volatile long richiestaTick = Long.MIN_VALUE;
    /** Quando ho chiesto io: finche' e' fresca, il mio segnale A dice "sto chiedendo". */
    private volatile long haChiestoTick = Long.MIN_VALUE;
    /**
     * I DUE CANALI DI SEGNALE, per ora MUTI (sempre 0).
     *
     * Sono quello che un'entita' EMETTE e che le altre percepiscono nel loro cono visivo (vedi
     * Vision.ENT_SIGNAL_A e B). Oggi nessuno li scrive: esistono perche' il dialogo, quando
     * arrivera', sara' solo un paio di USCITE in piu' della rete che riempiono due ingressi che
     * ci sono gia' - non una riscrittura della percezione. Il tracciato degli ingressi si cambia
     * una volta sola, e si paga una volta sola in cervelli salvati da trapiantare.
     *
     * Quando si accenderanno, la parte difficile non sara' il codice: un segnale non ha nessun
     * significato innato, quindi niente lo seleziona finche' non correla con qualcosa di utile.
     * Il posto e' pronto, la domanda e' aperta.
     */
    /**
     * IL PRIMO CANALE NON E' PIU' MUTO: dice "sto chiedendo di accoppiarmi".
     *
     * Non e' un campo che qualcuno aggiorna, e' lo stato letto al momento: vale 1 finche' la mia
     * richiesta e' fresca. Chi mi guarda lo vede nel suo settore (Vision.ENT_SIGNAL_A) e puo'
     * rispondere. Il secondo canale resta libero, per la prossima parola.
     */
    public double getSignalA(){
        if(ActionWeights.mateRequest <= 0){ return 0; }
        return Simulation.getTick() - haChiestoTick <= RICHIESTA_TTL ? 1.0 : 0.0;
    }

    public double getSignalB(){ return 0; }
    private double Netreward = 0;
    private double actionPoints = 0;
    private boolean sleeping = false;
    private String lastDamageCause = "fame";
    private String deathCause = "";
    private long deathTick = -1;
    private AtomicInteger[] position = new AtomicInteger[3];
    /**
     * Posizione CONTINUA in metri sul piano (la cella e' sempre il pavimento di questi valori:
     * vedi syncCell). Il mondo resta a celle per terreno, acqua e mele; l'entita' invece si
     * muove di quanto le permette il suo corpo, che non e' un numero intero di celle.
     */
    private volatile double exactX;
    private volatile double exactZ;
    /** Direzione verso cui guarda (radianti attorno a Y): la usa l'animazione e la fisica. */
    private volatile double facing = 0;
    public String process = "";
    public AtomicBoolean life = new AtomicBoolean();
    private AtomicInteger tick = new AtomicInteger();
    public NeuralNetwork Brain;

    // Il Genome e' immutabile: quando cambia si sostituisce il riferimento (letto anche dal thread grafico).
    private volatile Genome genome;
    private volatile EntityStats stats;

    private NeuralNetwork bestBrain;          // ultimo cervello "accettato"
    private double windowStartReward = 0;     // Netreward all'inizio della finestra
    private double bestWindowScore = Double.NEGATIVE_INFINITY;
    private int windowTick = 0;


    /** Conteggio globale delle azioni scelte (diagnostica: idle, 4 mosse, mangia, attacca, dormi, riproduci). */
    public static final long[] actionCounts = new long[OUTPUT_SIZE];

    private static Random rng(){
        return Simulation.random();
    }

    /** Entita' fondatrice: genoma casuale. */
    public entity(int id, int cycle, int x, int y, int z){
        this(id, cycle, x, y, z, Genome.random(SCHEMA, rng(), String.valueOf(id), cycle));
    }

    /** Entita' con genoma dato (es. figlio di due genitori). Le stat vitali derivano dal genoma. */
    public entity(int id, int cycle, int x, int y, int z, Genome genome){
        this.EntityID = id;
        this.genome = genome;
        this.stats = EntityStats.resolve(genome, SCHEMA);
        this.dietBias = stats.dietBias;   // si nasce con il gusto del gene, poi lo decidono i pasti
        this.healt = maxHealthInt();
        this.food = maxFood;
        this.thirst = MAX_THIRST;
        this.stamina = stats.maxStamina;
        this.born = cycle;
        this.age = 0;
        this.sex = rng().nextBoolean() ? 1 : 2;
        tick.set(0);
        position[0] = new AtomicInteger(x);
        position[1] = new AtomicInteger(y);
        position[2] = new AtomicInteger(z);
        this.exactX = x + 0.5;   // al centro della cella in cui nasce
        this.exactZ = z + 0.5;
        Brain = new NeuralNetwork(BRAIN_TOPOLOGY);

        bestBrain = Brain.copy();
        Brain.mutate(MUTATION_RATE);
        life.set(true);
    }

    // ------------------------------------------------------------------ salvataggio

    public JSONObject toJson(){
        JSONObject o = new JSONObject();
        o.put("id", EntityID);
        o.put("born", born);
        o.put("age", age);
        o.put("sex", sex);
        o.put("food", food);
        o.put("thirst", thirst);
        o.put("healt", healt);
        o.put("stamina", stamina);
        o.put("foodConsumed", consumedFood);
        o.put("applesEaten", applesEaten);
        o.put("kills", kills);
        o.put("reproCooldown", reproCooldown);
        o.put("dietbias", dietBias);
        o.put("netreward", Netreward);
        o.put("ticks", tick.get());
        JSONArray pos = new JSONArray();
        pos.put(position[0].get());
        pos.put(position[1].get());
        pos.put(position[2].get());
        o.put("pos", pos);
        o.put("exactX", exactX);
        o.put("exactZ", exactZ);
        o.put("facing", facing);
        o.put("gazeYaw", gazeYaw);
        o.put("gait", getGait().toJson());
        o.put("genome", genome.toJson());
        o.put("brain", BrainIO.toJson(Brain));
        o.put("bestBrain", BrainIO.toJson(bestBrain));
        return o;
    }

    /** Ricostruisce un'entita' salvata: genoma e cervelli tornano esattamente come erano. */
    public static entity fromJson(JSONObject o){
        Genome g = Genome.fromJson(o.getJSONObject("genome")).migrate(SCHEMA);
        JSONArray pos = o.getJSONArray("pos");
        entity e = new entity(o.getInt("id"), o.optInt("born", 0),
                pos.getInt(0), pos.getInt(1), pos.getInt(2), g);
        e.age = o.optInt("age", 0);
        e.sex = o.optInt("sex", e.sex);
        e.food = o.optInt("food", e.food);
        e.thirst = o.optInt("thirst", e.thirst);
        e.healt = o.optInt("healt", e.healt);
        e.stamina = Math.min(e.stats.maxStamina, o.optDouble("stamina", e.stamina));
        e.consumedFood = o.optInt("foodConsumed", 0);
        e.applesEaten = o.optInt("applesEaten", 0);
        e.kills = o.optInt("kills", 0);
        e.reproCooldown = o.optInt("reproCooldown", 0);
        // un salvataggio vecchio non ha l'abitudine: si riparte dalla predisposizione del gene
        e.dietBias = o.optDouble("dietbias", e.stats.dietBias);
        e.Netreward = o.optDouble("netreward", 0);
        e.tick.set(o.optInt("ticks", 0));
        // i salvataggi vecchi non hanno posizione continua ne' andatura: si ricavano dalla cella
        // e dal seme strutturato (come per un'entita' nuova)
        e.exactX = o.optDouble("exactX", pos.getInt(0) + 0.5);
        e.exactZ = o.optDouble("exactZ", pos.getInt(2) + 0.5);
        e.facing = o.optDouble("facing", 0);
        e.gazeYaw = o.optDouble("gazeYaw", 0);
        if(o.has("gait")){
            e.gait = src.body.Gait.fromJson(o.getJSONObject("gait"));
        }

        NeuralNetwork brainLoaded = loadBrain(o, "brain");
        NeuralNetwork bestLoaded = loadBrain(o, "bestBrain");
        if(brainLoaded != null){
            e.Brain = brainLoaded;
            e.bestBrain = bestLoaded != null ? bestLoaded : brainLoaded.copy();
        }
        return e;
    }

    private static NeuralNetwork loadBrain(JSONObject o, String key){
        if(!o.has(key)){ return null; }
        NeuralNetwork n = BrainIO.fromJson(o.getJSONObject(key));
        if(Arrays.equals(n.getTopology(), BRAIN_TOPOLOGY)){
            return n;
        }
        /*
         * UN CERVELLO DI UN ALTRO TRACCIATO NON SI RECUPERA, e per adesso va bene cosi'.
         *
         * C'era un trapianto che rimappava i pesi vecchi sui sensi nuovi - per SIGNIFICATO e non
         * per posizione, altrimenti sono fili incrociati. Funzionava ed era verificato, ed e'
         * stato tolto su decisione: finche' la rete e' in ristrutturazione i salvataggi vecchi
         * servono per guardarci dentro, non per farli ripartire. Se servira', e' nella storia di
         * git alla voce 17 del diario.
         */
        System.out.println("Cervello salvato con " + n.getTopology()[0] + " ingressi (adesso sono "
                + INPUT_SIZE + "): tracciato diverso, ne uso uno nuovo.");
        return null;
    }

    // ------------------------------------------------------------------ getter

    public int[] getPos(){
        int[] pos = new int[3];
        pos[0] = position[0].get();
        pos[1] = position[1].get();
        pos[2] = position[2].get();
        return pos;
    }

    public int getId(){ return EntityID; }
    public NeuralNetwork getBrain(){ return Brain; }
    NeuralNetwork getBestBrain(){ return bestBrain; }
    public void setProcessId(String value){ this.process = value; }

    public double getFitness(){
        int t = tick.get();
        return t > 0 ? Netreward / t : Netreward;
    }

    public int getSex(){return sex;}
    public int getFood(){return food;}
    public int getThirst(){return thirst;}
    public int getHealt(){return healt;}
    public double getStamina(){ return stamina; }
    public boolean isSleeping(){ return sleeping; }
    public int getKills(){ return kills; }
    public int getAge(){ return age; }
    public int getBorn(){ return born; }
    public int getReproCooldown(){ return reproCooldown; }
    public String getDeathCause(){ return deathCause; }
    public long getDeathTick(){ return deathTick; }
    public Genome getGenome(){ return genome; }
    public EntityStats getStats(){ return stats; }
    public static GenomeSchema getSchema(){ return SCHEMA; }

    // ------------------------------------------------------------------ corpo articolato

    // Cache del corpo: si ricostruisce solo se cambia il genoma o la fase di crescita (letto anche dal thread grafico).
    private volatile src.body.Automaton body;
    private volatile Genome bodyGenome;
    private volatile int bodyGrowthKey = -1;
    private volatile src.body.Automaton adultBody;
    private volatile Genome adultGenome;
    private volatile src.body.BodyTraits traits;
    private volatile src.body.Gait gait;
    private volatile boolean gaitInherited = false;
    private volatile src.body.Locomotion.Profile locomotion;
    /** Ogni quante azioni si ricontrolla se la misura fisica della locomozione e' arrivata. */
    private static final int LOCOMOTION_RECHECK = 25;
    /**
     * Ogni quante azioni l'entita' fa una prova di allenamento (vedi Locomotion.practice). Con
     * una vita media di qualche centinaio di azioni sono una decina di prove di fisica per vita,
     * che si sommano a quelle pagate alla nascita e si ereditano.
     */
    private static final int PRACTICE_INTERVAL = 40;

    /** Corpo articolato (automa) dell'entita': costruito dal suo piano del corpo (struttura mutabile), scalato dalla crescita. */
    public src.body.Automaton getBody(){
        Genome g = genome;
        int key = src.body.BodyParams.growthKey(age);
        src.body.Automaton b = body;
        if(b == null || bodyGenome != g || bodyGrowthKey != key){
            b = src.body.Automaton.fromPlan(g.bodyPlan(SCHEMA), src.body.BodyParams.growthFactor(age));
            body = b;
            bodyGenome = g;
            bodyGrowthKey = key;
        }
        return b;
    }

    // ---- tratti e locomozione: vedi src.body.BodyTraits e src.body.Locomotion ----

    /**
     * Corpo ADULTO (crescita 1) di questa entita'. Statistiche e locomozione si misurano su
     * questo, non sul corpo attuale: cosi' non cambiano a ogni tick di crescita e la misura
     * fisica si fa una volta per morfologia invece che una per eta'.
     */
    public src.body.Automaton getAdultBody(){
        Genome g = genome;
        src.body.Automaton a = adultBody;
        if(a == null || adultGenome != g){
            a = src.body.Automaton.fromPlan(g.bodyPlan(SCHEMA), 1.0);
            adultBody = a;
            adultGenome = g;
            traits = null;
            locomotion = null;
        }
        return a;
    }

    /** Massa, altezza, portata, arti di appoggio del corpo adulto. */
    public src.body.BodyTraits getTraits(){
        src.body.Automaton a = getAdultBody();
        src.body.BodyTraits t = traits;
        if(t == null){
            t = src.body.BodyTraits.of(a);
            traits = t;
        }
        return t;
    }

    /**
     * Andatura: ereditata dai genitori, ripresa dall'archivio delle sessioni precedenti, o
     * seminata a tavolino per chi non ha ne' l'una ne' l'altra.
     *
     * L'archivio viene PRIMA del seme a tavolino e DOPO i genitori: un figlio ha un'eredita' piu'
     * vicina di quella di uno sconosciuto di un'altra partita, mentre una fondatrice fino a ieri
     * non aveva nessuna eredita' e doveva ricominciare da un'andatura inventata (vedi
     * src.body.GaitArchive).
     */
    public src.body.Gait getGait(){
        src.body.Gait g = gait;
        if(g == null){
            if(ActionWeights.gaitArchive > 0){
                g = src.body.GaitArchive.andaturaPer(getAdultBody(), rng());
                /*
                 * NON si segna come EREDITATA, anche se e' un'eredita'. gaitInherited vale
                 * "l'andatura e' gia' stata ottimizzata su un corpo come questo, in questa
                 * sessione" e fa scendere il budget di prove da trialsPerBody (24) a
                 * trialsPerChild (8). Un'andatura dell'archivio invece viene da un'altra partita
                 * e magari da una classe di corpo vicina ma non identica: e' un punto di
                 * PARTENZA migliore, non un lavoro gia' fatto, e togliergli due terzi delle
                 * prove lo trasformerebbe in un peggioramento travestito da miglioramento.
                 */
            }
            if(g == null){
                g = src.body.Gait.seed(getAdultBody(), rng());
            }
            gait = g;
        }
        return g;
    }

    /**
     * Lascia all'archivio come si muoveva questa entita' e quanta strada ha fatto. Si chiama a
     * fine vita (Simulation.kill) e per chi e' ancora vivo a fine sessione.
     */
    void registraMovimento(){
        if(ActionWeights.gaitArchive <= 0){ return; }
        src.body.Locomotion.Profile p = locomotion;
        src.body.GaitArchive.registra(getAdultBody(), gait, distanzaPercorsa, applesEaten,
                age, p == null ? 0 : p.speed(), EntityID, src.Genealogy.getSession());
    }

    /** Un figlio parte dall'andatura incrociata dei genitori (come per il cervello). */
    void inheritGait(src.body.Gait g){
        this.gait = g;
        this.gaitInherited = true;
        this.locomotion = null;
    }

    /**
     * Profilo di locomozione: velocita' reale in m/s misurata facendo camminare il corpo nella
     * fisica. Non blocca: finche' la misura non e' pronta vale la stima geometrica, e si
     * richiede di nuovo ogni tanto per prendere il valore misurato quando arriva.
     */
    public src.body.Locomotion.Profile getLocomotion(){
        src.body.Locomotion.Profile p = locomotion;
        if(p == null || (!p.measured() && tick.get() % LOCOMOTION_RECHECK == 0)){
            // andatura ereditata: una sola misura; fondatrice: ottimizzazione completa
            p = src.body.Locomotion.profile(getAdultBody(), getGait(),
                    EntityID * 7919L + Simulation.getSeed(),
                    gaitInherited ? src.body.Locomotion.trialsPerChild
                                  : src.body.Locomotion.trialsPerBody);
            // Quando la fisica risponde, l'entita' ADOTTA l'andatura con cui ha camminato: e'
            // quella che i figli erediteranno. Senza questo passaggio la fisica misurerebbe
            // un'andatura che nessuno usa, e l'evoluzione del controllore si fermerebbe.
            if(p.measured() && p.gait() != null && p.gait() != this.gait){
                this.gait = p.gait();
                this.gaitInherited = true;
                src.body.Locomotion.remember(getAdultBody(), p.gait(), p);
            }
            locomotion = p;
        }
        return p;
    }

    /** Velocita' di camminata attuale (m/s): quella del corpo adulto ridotta dalla crescita. */
    public double getWalkSpeed(){
        return getLocomotion().speed() * src.body.BodyParams.growthFactor(age);
    }

    /**
     * Quanti metri copre una singola azione di movimento: una cella per il corpo di riferimento,
     * piu' o meno a seconda di quanto quel corpo cammina meglio o peggio (vedi
     * src.body.Locomotion.REFERENCE_SPEED). Il minimo evita che un corpo che la fisica non riesce
     * a far camminare resti inchiodato a terra per sempre: striscia, lentamente.
     */
    public double stepLength(){
        if(ActionWeights.bodyGaitSpeed <= 0){
            return CELL_METERS;
        }
        double ratio = getWalkSpeed() / src.body.Locomotion.REFERENCE_SPEED;
        return CELL_METERS * Math.max(MIN_STEP_RATIO, Math.min(MAX_STEP_RATIO, ratio));
    }

    /**
     * Vero se questo corpo cammina gia': il suo passo arriva almeno alla frazione
     * ActionWeights.svezzamentoSoglia del corpo di riferimento. Chi cammina gia' non ha bisogno
     * di essere svezzato.
     */
    private boolean camminaGia(){
        if(ActionWeights.bodyGaitSpeed <= 0){ return true; }
        return getWalkSpeed() >= ActionWeights.svezzamentoSoglia
                * src.body.Locomotion.REFERENCE_SPEED;
    }

    /** Un corpo che non cammina striscia a un terzo di passo; il migliore arriva al doppio. */
    private static final double MIN_STEP_RATIO = 0.35;
    private static final double MAX_STEP_RATIO = 2.0;

    /** Secondi di mondo che passano tra un'azione e la successiva (dal gene speed). */
    private double secondsPerAction(){
        double pointsPerTick = Math.max(1e-6, stats.speed / SPEED_DIVISOR);
        return TICK_SECONDS / pointsPerTick;
    }
    public boolean isAlive(){ return life.get(); }
    public double getNetreward(){return Netreward;}
    public int getFoodConsumed(){ return consumedFood; }
    public int getApplesEaten(){ return applesEaten; }
    public boolean isMature(){ return age >= MATURITY_AGE; }

    public void setFood(int value){ this.food = value; }
    public void setThirst(int value){ this.thirst = value; }
    /** Le entita' fondatrici partono adulte. */
    public void makeAdult(){ this.age = MATURITY_AGE; }

    /** Un figlio parte con il cervello incrociato dei genitori. */
    void inheritBrain(NeuralNetwork b){
        this.Brain = b;
        this.bestBrain = b.copy();
    }

    /**
     * Istinti di base per le entita' FONDATRICI (non per i figli, che ereditano dai genitori):
     * pesi iniziali che collegano la vista alle azioni giuste (mangiare cio' che si ha sotto i
     * piedi, avvicinarsi al cibo visibile, dormire quando la stamina e' bassa, riprodursi
     * quando c'e' un'altra entita' vicina). Sono solo il punto di partenza: la mutazione e la
     * selezione li modificano. Senza questo seme le reti casuali non trovano quasi mai da
     * mangiare e la prima popolazione si estingue prima che l'evoluzione parta.
     */
    public void seedInstincts(){
        final int aBase = Vision.IDX_OBJ + Vision.CAT_APPLE * Vision.SECTORS;
        final int wBase = Vision.IDX_OBJ + Vision.CAT_WATER * Vision.SECTORS;
        final int cBase = Vision.IDX_OBJ + Vision.CAT_CORPSE * Vision.SECTORS;
        // "qualcuno nei paraggi" e' la VICINANZA delle altre entita', il primo dei loro
        // attributi: l'istinto di partenza non sa niente di mole, vigore o sguardo, e nemmeno di
        // chi sia l'altro. Deve impararlo.
        final int eBase = Vision.IDX_ENT + Vision.ENT_NEAR * Vision.SECTORS;
        final int sBase = Vision.IDX_ENT + Vision.ENT_SIGNAL_A * Vision.SECTORS;
        final int hereApple = Vision.IDX_HERE + Vision.CAT_APPLE;
        final int hereWater = Vision.IDX_HERE + Vision.CAT_WATER;
        final int hereCorpse = Vision.IDX_HERE + Vision.CAT_CORPSE;
        final int nothing = Vision.IDX_NOTHING;
        final int self = Vision.SIZE;
        final int inFood = self + 1, inThirst = self + 2, inStamina = self + 3, inMature = self + 6;
        final int inRemRight = self + 10, inRemLeft = self + 11;
        // self + 12 e' la dieta e self + 13 la luce: nessun istinto innato ne' sull'una ne'
        // sull'altra (per la luce vedi il commento qui sotto; per la dieta vale lo stesso
        // ragionamento - un peso di partenza deciderebbe noi quello che deve decidere la fame)
        /*
         * NESSUN ISTINTO SULLA LUCE. L'ingresso della luce del giorno (self + 13) esiste e la
         * rete lo riceve, ma il cablaggio di partenza non gli da' alcun peso.
         *
         * PERCHE'. Un peso innato "col buio fermati" sembra ragionevole e invece decide in partenza
         * la cosa che volevamo far decidere alla selezione: con quel peso ogni entita' nasce
         * diurna, la notte la passa ferma, e il gene della visione notturna non viene mai messo
         * alla prova - un gene che nessuno usa non puo' essere premiato, quindi non evolve. Senza
         * peso iniziale la luce e' solo un'informazione, e diurne e notturne partono pari.
         */

        // il cono e' diviso in otto settori da sinistra a destra: i due centrali sono il muso
        final int[] left = {0, 1, 2};
        final int[] middle = {3, 4};
        final int[] right = {5, 6, 7};

        // --- Strato 1: un neurone dedicato per intenzione
        // avanti: cibo o acqua davanti al muso
        double[] fwd = new double[INPUT_SIZE];
        for(int sct : middle){
            fwd[aBase + sct] = ActionWeights.n_moveGain;
            fwd[cBase + sct] = ActionWeights.n_moveGain;
            fwd[wBase + sct] = ActionWeights.n_moveGain / 2;   // come nel neurone di rotazione
            // (qui il freno della sete c'e' gia': fwd[inThirst] = n_moveThirst, piu' sotto)
        }
        fwd[inFood] = ActionWeights.n_moveFood;
        fwd[inThirst] = ActionWeights.n_moveThirst;
        fwd[hereWater] = ActionWeights.n_moveHereWater;
        // SE NON VEDO NIENTE, CAMMINO. Girare sul posto non cambia il posto, e il posto e'
        // l'unica cosa che decide se davanti ci sara' qualcosa: guardare lo fa la testa, che e'
        // gratis. Senza questo peso un'entita' in un mondo con il cibo concentrato sugli alberi
        // passa la vita a girarsi dove e' nata.
        // CI SI AVVICINA, POI SI CHIEDE: qualcuno davanti e' un motivo per camminare (vedi
        // ActionWeights.n_moveMate). Senza questo, la richiesta di accoppiamento aspetta che il
        // caso porti l'altra entro le tre celle della portata.
        if(ActionWeights.n_moveMate != 0){
            for(int sct : middle){
                fwd[eBase + sct] = ActionWeights.n_moveMate;
            }
        }
        fwd[nothing] = ActionWeights.n_moveNothing;
        setNeuron(0, 0, fwd, ActionWeights.n_moveBias);

        // girare il corpo: cibo di lato. La spinta del "non vedo niente" va SOLO a destra, non
        // ai due lati: con lo stesso peso su entrambi i neuroni le due rotazioni si annullavano e
        // l'entita' oscillava di mezzo passo d'angolo avanti e indietro - nel primo mondo con gli
        // alberi erano 7900 giri del corpo su 13000 azioni, e 450 passi. Asimmetrica, la spinta
        // diventa un ARCO: insieme al passo avanti e' una traiettoria che copre terreno nuovo.
        setNeuron(0, 1, turnNeuron(right, left, aBase, cBase, wBase, eBase, nothing, inThirst,
                ActionWeights.n_turnNothing), ActionWeights.n_moveBias);
        setNeuron(0, 2, turnNeuron(left, right, aBase, cBase, wBase, eBase, nothing, inThirst, 0),
                ActionWeights.n_moveBias);

        // girare la testa: se non vedo niente, guardo da dove mi resta collo
        double[] scanR = new double[INPUT_SIZE];
        scanR[nothing] = ActionWeights.n_scanGain;
        scanR[inRemRight] = ActionWeights.n_scanGain / 3;
        setNeuron(0, 3, scanR, ActionWeights.n_scanBias);
        double[] scanL = new double[INPUT_SIZE];
        scanL[nothing] = ActionWeights.n_scanGain;
        scanL[inRemLeft] = ActionWeights.n_scanGain / 3;
        setNeuron(0, 4, scanL, ActionWeights.n_scanBias);

        // mangiare cio' che si ha a portata
        double[] eat = new double[INPUT_SIZE];
        eat[hereApple] = ActionWeights.n_eatGain;
        eat[hereCorpse] = ActionWeights.n_eatGain;
        eat[inFood] = ActionWeights.n_eatFood;
        setNeuron(0, 5, eat, ActionWeights.n_eatBias);

        // bere: acqua vicina e sete
        double[] drink = new double[INPUT_SIZE];
        for(int sct = 0; sct < Vision.SECTORS; sct++){ drink[wBase + sct] = ActionWeights.n_drinkGain; }
        drink[hereWater] = ActionWeights.n_drinkHere;
        drink[inThirst] = ActionWeights.n_drinkThirst;
        setNeuron(0, 6, drink, ActionWeights.n_drinkBias);

        // dormire quando la stamina e' bassa
        double[] rest = new double[INPUT_SIZE];
        rest[inStamina] = ActionWeights.n_restGain;
        setNeuron(0, 7, rest, ActionWeights.n_restBias);

        // riprodursi: sazia, matura, e qualcuno nei paraggi
        double[] mate = new double[INPUT_SIZE];
        mate[inFood] = ActionWeights.n_mateFood;
        mate[inMature] = ActionWeights.n_mateMature;
        for(int sct = 0; sct < Vision.SECTORS; sct++){
            mate[eBase + sct] = ActionWeights.n_mateEntity;
            // "quello li' sta chiedendo": l'istinto di RISPONDERE a una richiesta. Serve perche'
            // senza di esso una richiesta non ha nessun significato innato, e due entita'
            // potrebbero non accoppiarsi mai pur essendo entrambe disponibili. Il peso e' un
            // punto di partenza: chi accetta chiunque e chi e' selettivo partono da qui.
            mate[sBase + sct] = ActionWeights.n_mateSignal;
        }
        setNeuron(0, 8, mate, ActionWeights.n_mateBias);

        // --- Strato 2: h2 = sigmoid(4*h1 - 2), spinta graduata tra ~0.12 e ~0.88
        for(int j = 0; j < INTENTIONS; j++){
            double[] w = new double[BRAIN_TOPOLOGY[1]];
            w[j] = 4;
            setNeuron(1, j, w, -2);
        }

        // --- Uscite: out = sigmoid(8*h2 - 4). Ferma, indietro e attacca partono quasi spente.
        int[] outputs = {ACT_FORWARD, ACT_TURN_RIGHT, ACT_TURN_LEFT, ACT_HEAD_RIGHT, ACT_HEAD_LEFT,
                ACT_EAT, ACT_DRINK, ACT_SLEEP, ACT_REPRODUCE};
        for(int j = 0; j < outputs.length; j++){
            double[] w = new double[BRAIN_TOPOLOGY[2]];
            w[j] = 8;
            setNeuron(2, outputs[j], w, -4);
        }
        for(int a : new int[]{ACT_IDLE, ACT_BACK, ACT_ATTACK}){
            setNeuron(2, a, new double[BRAIN_TOPOLOGY[2]], ActionWeights.n_idleBias);
        }
        bestBrain = Brain.copy();
    }

    /** Quante intenzioni hanno un neurone dedicato negli istinti iniziali. */
    private static final int INTENTIONS = 9;

    /**
     * Neurone "gira il corpo da questo lato".
     *
     * RISPONDE A UNA DIFFERENZA, NON A UNA PRESENZA: i settori del proprio lato pesano positivo,
     * quelli del lato opposto NEGATIVO con lo stesso peso. Una scena simmetrica si annulla a
     * zero e solo un vero squilibrio tra destra e sinistra produce una rotazione.
     *
     * PERCHE' COSI'. Con i pesi solo sul proprio lato bastava qualcosa di diffuso - l'acqua, che
     * in un mondo con 376 colonne allagate si vede quasi sempre da tutte le parti - per saturare
     * TUTTI E DUE i neuroni insieme: l'entita' aveva un motivo forte per girare a destra e lo
     * stesso motivo per girare a sinistra, e passava la vita a oscillare. Nel primo mondo con gli
     * alberi erano 13700 giri del corpo su 24000 azioni, con 1128 passi avanti, e la popolazione
     * si estingueva al tick 2400. Un neurone che misura una differenza non puo' avere quel
     * problema: e' la differenza stessa a dirgli da che parte girare.
     *
     * L'acqua pesa la META' del cibo ED E' SPENTA DA CHI NON HA SETE (il peso negativo su
     * inThirst, che vale 1 quando si e' dissetati).
     *
     * PERCHE' LA SETE C'ENTRA. Senza quel freno l'acqua guidava le rotazioni sempre, e un lago
     * e' un bersaglio ENORME e DIFFUSO: l'entita' lo vedeva un po' piu' a destra, girava di 22
     * gradi, ora lo vedeva un po' piu' a sinistra, rigirava - un ciclo limite a due stati, con le
     * uscite che alternavano 0.93/0.12 e 0.08/0.94 per tutta la vita. Il bersaglio non si
     * raggiunge mai perche' non e' un punto: e' meta' dell'orizzonte. Il cibo invece e' un punto,
     * e verso un punto la retroazione negativa converge. Quindi l'acqua resta un motivo per
     * cambiare strada solo quando serve bere, che e' anche l'unico momento in cui ha senso.
     */
    private double[] turnNeuron(int[] mySide, int[] otherSide, int aBase, int cBase, int wBase,
                                int eBase, int nothing, int inThirst, double nothingWeight){
        double[] w = new double[INPUT_SIZE];
        double gain = ActionWeights.n_moveGain;
        double mate = ActionWeights.n_moveMate;
        for(int sct : mySide){
            w[aBase + sct] = gain;
            w[cBase + sct] = gain;
            w[wBase + sct] = gain / 2;
            // le altre entita' entrano nella DIFFERENZA come tutto il resto: una scena simmetrica
            // non deve produrre nessuna rotazione (vedi l'intestazione di questo metodo)
            w[eBase + sct] = mate;
        }
        for(int sct : otherSide){
            w[aBase + sct] = -gain;
            w[cBase + sct] = -gain;
            w[wBase + sct] = -gain / 2;
            w[eBase + sct] = -mate;
        }
        w[nothing] = nothingWeight;
        w[inThirst] = ActionWeights.n_turnSated;
        return w;
    }

    private void setNeuron(int layer, int index, double[] weights, double bias){
        brain.Neuron n = Brain.getLayer(layer).getNeuron(index);
        n.setWeights(weights);
        n.setBias(bias);
    }

    void markDead(String cause, long atTick){
        this.deathCause = cause;
        this.deathTick = atTick;
        this.sleeping = false;
    }

    // ------------------------------------------------------------------ input della rete

    /** Vista a settori (fissa) + stato interno: sempre INPUT_SIZE valori. */
    public double[] get_NetInput(){
        double[] vision = Vision.sense(this);
        double[] input = new double[INPUT_SIZE];
        System.arraycopy(vision, 0, input, 0, vision.length);
        int i = vision.length;
        input[i++] = healt / stats.maxHealth;
        // Normalizzato sul valore standard (200) e limitato a 1: con la riserva alzata per
        // l'addestramento la rete vede gli stessi valori che vedra' nelle simulazioni normali.
        input[i++] = Math.min(1.0, food / (double) MAX_FOOD);
        input[i++] = thirst / (double) MAX_THIRST;
        input[i++] = stamina / stats.maxStamina;
        input[i++] = position[0].get() / (double) world.getDim();
        input[i++] = position[2].get() / (double) world.getDim();
        input[i++] = Math.min(1.0, age / (double) MATURITY_AGE);
        input[i++] = sex == 2 ? 1.0 : 0.0;
        // propriocezione dello sguardo: dove ho la testa, e quanto collo mi resta nei due versi
        double range = gazeRange();
        input[i++] = Math.sin(gazeYaw);
        input[i++] = Math.cos(gazeYaw);
        input[i++] = range > 1e-3 ? (range - gazeYaw) / (2 * range) : 0.0;
        input[i++] = range > 1e-3 ? (gazeYaw + range) / (2 * range) : 0.0;
        // la propria abitudine alimentare: senza questo ingresso un'entita' non ha modo di
        // sapere quale cibo le conviene, e la specializzazione resta una cosa che le capita
        // addosso invece di una cosa che puo' inseguire
        input[i++] = dietBias;
        input[i++] = DayNight.daylight();
        return input;
    }

    // ------------------------------------------------------------------ ciclo di vita

    /** Chiamato dal ciclo centrale a ogni tick: accumula punti azione e agisce quando arriva a 1. */
    public void advance(){
        metabolism();
        actionPoints += stats.speed / SPEED_DIVISOR;
        if(actionPoints >= 1.0){
            actionPoints -= 1.0;
            step();
        }
    }

    /**
     * Fame, sete, danno da inedia e guarigione legati al TEMPO, non alle azioni (vedi
     * STATO_LAVORO.txt voce 12: finche' il tempo non costa, la strategia vincente e' la torpore -
     * la 120 ha vissuto 27000 tick facendo 3672 azioni e mangiando 33 mele).
     *
     * La taratura tiene la media di prima: il consumo era di 1 per azione e un'entita' media fa
     * speed/SPEED_DIVISOR = 1.75/7 = 0.25 azioni per tick, quindi 0.25 per tick e' lo stesso
     * ritmo per chi ha velocita' media. Cambia per gli estremi, ed e' il punto: un'entita' lenta
     * non guadagna piu' vita stando ferma, una veloce non la paga due volte.
     *
     * La massa entra nel consumo di base perche' un corpo grosso brucia di piu' anche da fermo:
     * cosi' essere massicci ha un prezzo continuo, non solo il costo del passo.
     */
    private void metabolism(){
        double rate = BASAL_PER_TICK;
        if(ActionWeights.bodyMassStats > 0){
            rate *= getTraits().massFactor();
        }
        // il fiato torna anche da svegli, piano: dormire resta il modo rapido
        if(!sleeping && stamina < stats.maxStamina){
            stamina = Math.min(stats.maxStamina,
                    stamina + stats.maxStamina * WAKE_REGEN_FRACTION);
        }
        // dormire dimezza il consumo di cibo e azzera quello d'acqua, come nel vecchio ciclo
        /*
         * IL PREMIO VIENE DAL CORPO, NON DA UN ORACOLO, E SI PAGA A TEMPO.
         *
         * Al posto del vecchio "+0.5 per un passo sopravvissuto" (denso ma IDENTICO in ogni
         * finestra, quindi incapace di distinguere una finestra buona da una vuota) c'e' il
         * bisogno che fa male: piu' si ha fame e sete, piu' costa esistere. Segnale denso, viene
         * dal proprio stato, e NON INDICA NESSUNA DIREZIONE - sentire la fame non dice dov'e'
         * la mela.
         *
         * STA QUI E NON IN step() PERCHE' SI PAGA PER TICK, come il metabolismo. Messo per
         * azione, un'entita' lenta ne pagava meno a parita' di tempo di mondo pur consumando lo
         * stesso cibo: era la torpore della voce 11 che rientrava dalla finestra, stavolta nel
         * segnale di apprendimento invece che nel cibo.
         */
        double fameRel = 1.0 - Math.min(1.0, food / (double) maxFood);
        double seteRel = 1.0 - Math.min(1.0, thirst / (double) MAX_THIRST);
        Netreward -= ActionWeights.needPain * (fameRel + seteRel);

        /*
         * SVEZZAMENTO: finche' non sa camminare, e finche' e' un cucciolo, il mondo lo nutre.
         * Vedi ActionWeights.svezzamento per il perche': un corpo che deve ancora imparare a
         * camminare muore di fame prima di averlo imparato, e il tempo per imparare non glielo
         * puo' dare altro che il non morire.
         */
        if(ActionWeights.svezzamento > 0 && age < MATURITY_AGE && !camminaGia()){
            rate *= 1.0 - Math.min(1.0, ActionWeights.svezzamento);
        }

        foodDrain += sleeping ? rate * 0.5 : rate;
        thirstDrain += sleeping ? 0 : rate;
        while(foodDrain >= 1.0){ foodDrain -= 1.0; food--; }
        while(thirstDrain >= 1.0){ thirstDrain -= 1.0; thirst--; }
        if(food < 0){ food = 0; }
        if(thirst < 0){ thirst = 0; }

        if(food <= 0 || thirst <= 0){
            starveDamage += STARVE_DAMAGE_PER_TICK;
            if(starveDamage >= 1.0){
                int dmg = (int) starveDamage;
                starveDamage -= dmg;
                takeDamage(dmg, food <= 0 ? "fame" : "sete");
            }
        }else if(food >= 85 && thirst >= 85 && healt < maxHealthInt()){
            healing += BASAL_PER_TICK;
            if(healing >= 1.0){
                healing -= 1.0;
                healt++;
            }
        }
        if(healt <= 0 && life.get()){
            Simulation.kill(this, lastDamageCause);
        }
    }

    /**
     * Consumo di base per tick di mondo: 0.25 e' il consumo per azione di prima (1) moltiplicato
     * per le azioni al tick di un'entita' di velocita' media (1.75/7).
     */
    private static final double BASAL_PER_TICK = 0.25;
    /** Danno da inedia per tick: 3 per azione di prima, per 0.25 azioni al tick. */
    private static final double STARVE_DAMAGE_PER_TICK = 0.75;
    private double foodDrain, thirstDrain, starveDamage, healing;

    private void step(){
        if(!life.get()){ return; }
        tick.addAndGet(1);
        age++;
        applyAgeDrift();
        if(reproCooldown > 0){ reproCooldown--; }
        sleeping = false;

        double[] output = Brain.predict(get_NetInput());
        perform(chooseAction(output));
        if(!life.get()){ return; }

        // Fame, sete e salute stanno in metabolism(), che il ciclo centrale chiama a ogni tick.
        // Qui c'era anche il VECCHIO consumo per AZIONE, dietro un interruttore che nessun
        // profilo metteva a zero: un ramo morto che duplicava la stessa logica con altre
        // costanti (danno 3 invece di 0.75 al tick), pronto a divergere senza che si notasse.

        // Finestra di valutazione: tengo la mutazione del cervello solo se ha fatto uguale o meglio
        windowTick++;
        if (windowTick >= WINDOW_SIZE) {
            double score = (Netreward - windowStartReward) / windowTick;
            /*
             * CONFRONTO STRETTO. Prima era "score >= bestWindowScore", e con il vecchio premio
             * fisso di 0.5 per passo una finestra in cui non succedeva niente valeva ESATTAMENTE
             * 0.5 come la precedente: il confronto passava, il cervello di riferimento veniva
             * riscritto e si mutava di nuovo. Risultato: in tutte le finestre vuote il cervello
             * faceva una passeggiata casuale - il 10% dei pesi spostati di 0.25 ogni dieci passi.
             * Qualche centinaio di passi e gli istinti di partenza non esistevano piu'. E' la
             * spiegazione del cervello della #10 (bias a destra, "mangia" al 44%): non era un
             * difetto del cablaggio iniziale, era deriva.
             *
             * Con il confronto stretto una finestra piatta ANNULLA la mutazione invece di tenerla.
             */
            if (score > bestWindowScore) {
                bestBrain = Brain.copy();
                bestWindowScore = score;
            } else {
                Brain = bestBrain.copy();
                /*
                 * E la soglia scende piano verso il punteggio recente. Senza questo il confronto
                 * stretto avrebbe il difetto opposto: una finestra fortunata (due pasti di fila)
                 * resterebbe imbattibile per sempre e il cervello si congelerebbe a vita.
                 */
                bestWindowScore += (score - bestWindowScore) * ActionWeights.windowDecay;
            }
            Brain.mutate(MUTATION_RATE);
            windowStartReward = Netreward;
            windowTick = 0;
        }

        // la stessa cosa, sul modo di camminare invece che sul cervello
        practiceWalking();

        if(healt <= 0){
            Simulation.kill(this, lastDamageCause);
        }
    }

    /**
     * Si allena a camminare, vivendo: ritira l'andatura che la prova precedente ha trovato (se e'
     * andata meglio di quella che usava) e ogni tanto ne chiede un'altra.
     *
     * Sta accanto alla finestra di valutazione del cervello perche' e' lo stesso meccanismo: si
     * prova una variazione, si tiene se migliora. La differenza e' che il punteggio non lo da' la
     * vita ma la fisica, su un thread a parte: provare un'andatura davvero vivendo vorrebbe dire
     * camminare peggio per un po', e in un mondo dove si muore di fame non si puo'.
     *
     * Quello che impara non resta a lei: i figli partono dall'andatura incrociata dei genitori
     * (vedi inheritGait e Genome.reproduce), quindi l'allenamento di una vita e' il punto di
     * partenza della successiva.
     */
    private void practiceWalking(){
        src.body.Locomotion.Profile better = src.body.Locomotion.collectPractice(EntityID);
        if(better != null){
            this.gait = better.gait();
            this.gaitInherited = true;
            this.locomotion = better;
        }
        src.body.Locomotion.Profile p = locomotion;
        if(p != null && p.measured() && tick.get() % PRACTICE_INTERVAL == 0){
            src.body.Locomotion.practice(EntityID, getAdultBody(), p,
                    EntityID * 7919L + Simulation.getSeed() + tick.get());
        }
    }

    private int chooseAction(double[] output){
        boolean mask = ActionWeights.maskActions > 0;
        boolean[] ok = mask ? feasibleActions() : null;
        if(rng().nextDouble() < EXPLORATION){
            if(!mask){ return rng().nextInt(OUTPUT_SIZE); }
            int n = 0;
            for(boolean f : ok){ if(f){ n++; } }
            int pick = rng().nextInt(n);
            for(int i = 0; i < ok.length; i++){
                if(ok[i] && pick-- == 0){ return i; }
            }
        }
        int best = -1;
        for (int i = 0; i < output.length; i++) {
            if(mask && !ok[i]){ continue; }
            if (best < 0 || output[i] > output[best]) {
                best = i;
            }
        }
        return best < 0 ? ACT_IDLE : best;
    }

    /** Quali azioni, in questo momento, possono davvero riuscire (stessi controlli di perform()). */
    private boolean[] feasibleActions(){
        boolean[] ok = new boolean[OUTPUT_SIZE];
        int x = position[0].get(), y = position[1].get(), z = position[2].get();
        int dim = world.getDim();
        ok[ACT_IDLE] = true;
        ok[ACT_SLEEP] = true;
        // avanti e indietro: si guarda dove si finirebbe davvero, con il passo di questo corpo
        double cost = moveCost();
        for(int sign = -1; sign <= 1; sign += 2){
            double step = stepLength() * sign;
            int nx = cellOf(exactX + Math.sin(facing) * step);
            int nz = cellOf(exactZ + Math.cos(facing) * step);
            boolean can = stamina >= cost && nx >= 0 && nx < dim && nz >= 0 && nz < dim
                    && !world.isWaterColumn(nx, nz);
            ok[sign > 0 ? ACT_FORWARD : ACT_BACK] = can;
        }
        ok[ACT_TURN_RIGHT] = stamina >= TURN_COST;
        ok[ACT_TURN_LEFT] = stamina >= TURN_COST;
        // la testa si gira solo se il collo ha ancora corsa in quel verso
        double range = gazeRange();
        ok[ACT_HEAD_RIGHT] = range > 1e-3 && gazeYaw < range - 1e-3;
        ok[ACT_HEAD_LEFT] = range > 1e-3 && gazeYaw > -range + 1e-3;
        ok[ACT_EAT] = stamina >= EAT_COST && (appleWithinReach() != null || corpseWithinReach() != null);
        boolean water = false;
        for(int dx = -1; dx <= 1 && !water; dx++){
            for(int dz = -1; dz <= 1; dz++){
                int wx = x + dx, wz = z + dz;
                if(wx < 0 || wx >= dim || wz < 0 || wz >= dim){ continue; }
                // l'acqua si raggiunge da una cella adiacente, ma la si deve VEDERE (o avere
                // sotto i piedi): vedi percepibile()
                if(world.isWaterColumn(wx, wz) && percepibile(wx, wz)){ water = true; break; }
            }
        }
        ok[ACT_DRINK] = stamina >= 1 && water;
        // Attaccare e' sempre possibile (con stamina sufficiente): anche senza nessuno nei dintorni, il colpo va a vuoto
        ok[ACT_ATTACK] = ActionWeights.allowAttack > 0 && stamina >= ATTACK_COST;
        /*
         * ACCOPPIARSI: con ActionWeights.mateRequest l'azione vale sia per CHIEDERE sia per
         * ACCETTARE, quindi e' fattibile se c'e' qualcuno a cui chiedere OPPURE qualcuno che ha
         * chiesto a me. La stamina richiesta e' quella della richiesta, piu' bassa: accettare
         * costa di piu' ma chi accetta sta per fare un figlio, non una domanda.
         */
        boolean puoRiprodursi = age >= MATURITY_AGE && reproCooldown <= 0 && food >= REPRO_FOOD_MIN
                && Entity_manager.Entity_count() < Simulation.maxPopulation();
        if(ActionWeights.mateRequest > 0){
            // RISPONDERE non e' INIZIARE. Chi accetta fa la parte del partner: gli basta
            // canMate (60 di cibo) e non paga ne' stamina ne' ricerca, perche' il costo lo paga
            // chi ha chiesto. Prima questa riga chiedeva puoRiprodursi (80 di cibo) anche a chi
            // doveva solo dire si', e per via del corto circuito di && la richiesta non veniva
            // nemmeno GUARDATA da chi non era in condizione di chiedere: la domanda arrivava a
            // un'entita' sorda. Vedi STATO_LAVORO.txt voce 19.
            boolean puoAccettare = canMate()
                    && Entity_manager.Entity_count() < Simulation.maxPopulation()
                    && richiedente() != null;
            ok[ACT_REPRODUCE] = puoAccettare
                    || (puoRiprodursi && stamina >= RICHIESTA_COST && findPartnerNoRandom());
        }else{
            ok[ACT_REPRODUCE] = puoRiprodursi && stamina >= REPRODUCE_COST && findPartnerNoRandom();
        }
        return ok;
    }

    /**
     * Distanza entro cui si trova un partner: resta PARTNER_RANGE, ma un corpo con arti molto
     * lunghi arriva piu' lontano. Non si restringe mai, perche' le nascite sono il collo di
     * bottiglia della popolazione (vedi STATO_LAVORO.txt).
     */
    private double partnerRange(){
        return ActionWeights.bodyReach > 0 ? Math.max(PARTNER_RANGE, reachNow()) : PARTNER_RANGE;
    }

    private boolean findPartnerNoRandom(){
        int y = position[1].get();
        double range = partnerRange();
        for(entity o : Entity_manager.snapshotAlive()){
            if(o == this || o.sex == this.sex || !o.canMate()){ continue; }
            int[] op = o.getPos();
            if(reachableLevel(y, op[1]) && distanceTo(o) <= range
                    && percepibile(op[0], op[2])){ return true; }
        }
        return false;
    }

    private void perform(int action){
        actionCounts[action]++;
        switch (action) {
            case ACT_IDLE:
                Netreward -= 0.5;
                break;
            case ACT_FORWARD: walk(1); break;
            case ACT_BACK: walk(-1); break;
            case ACT_TURN_RIGHT: turn(TURN_STEP); break;
            case ACT_TURN_LEFT: turn(-TURN_STEP); break;
            case ACT_HEAD_RIGHT: turnHead(HEAD_STEP); break;
            case ACT_HEAD_LEFT: turnHead(-HEAD_STEP); break;
            case ACT_EAT:
                if(spend(EAT_COST)){ eat(); }
                break;
            case ACT_ATTACK:
                if(spend(ATTACK_COST)){ attack(); }
                break;
            case ACT_SLEEP:
                sleep();
                break;
            case ACT_REPRODUCE:
                if(ActionWeights.mateRequest > 0){
                    entity chiedente = richiedente();
                    if(chiedente != null){
                        accetta(chiedente);
                    }else{
                        chiedi();
                    }
                }else{
                    reproduce();
                }
                break;
            case ACT_DRINK:
                drink();
                break;
            default:
                throw new AssertionError();
        }
    }

    /** Consuma stamina: se non basta l'azione fallisce (l'entita' e' troppo stanca). */
    private boolean spend(double cost){
        if(stamina < cost){
            Netreward -= 1.0;
            return false;
        }
        stamina -= cost;
        return true;
    }

    /**
     * Un passo nel verso del muso (sign = +1 avanti, -1 indietro). La DISTANZA non e' piu' una cella fissa: e' quanta
     * strada il corpo riesce a fare in un'azione con la sua andatura (vedi stepLength), quindi
     * gambe lunghe e andatura efficace portano piu' lontano a parita' di azioni. La cella resta
     * il riferimento del mondo (terreno, acqua, mele) ed e' sempre il pavimento della posizione
     * continua.
     */
    private void walk(double sign){
        /*
         * L'INTENZIONE SI SEGNA SUBITO, prima di sapere se il passo riuscira'. E' quella che fa
         * muovere le gambe nel mondo fisico (vedi Palco.Candidato.cammina), e deve essere
         * l'INTENZIONE e non il risultato: legandola a isWalking(), che vuol dire "si e' mosso",
         * nasce un circolo vizioso misurato il 24/10/2026 - zero metri dalla fisica, quindi non
         * sta camminando, quindi il palco gli ferma le gambe, quindi zero metri per sempre. Nel
         * lotto di prova faceva crollare la velocita' vera da 0.2 a 0.008 m/s e le nascite da 84
         * a 11. Chi prova a camminare e non ce la fa deve continuare a provare: e' esattamente
         * quello che significa imparare a camminare.
         */
        lastWalkWishTick = Simulation.getTick();
        int dim = world.getDim();
        /*
         * SE E' SUL PALCO, IL PASSO LO DECIDE LA FISICA. Non la formula
         * (velocita' misurata / velocita' di riferimento) ma i metri che la sua ragdoll ha
         * davvero coperto in questo tick di mondo: se e' caduta, il passo e' zero e l'entita'
         * resta dov'e'. Il tetto e' la falcata geometrica, cosi' il credito dei tick in cui non
         * si e' mossa non diventa un salto (vedi src.body.Palco.consuma).
         *
         * Indietro vale quanto avanti: la ragdoll cammina in una sola direzione, e un controllore
         * che sa anche tornare indietro non c'e'. E' una approssimazione dichiarata, non una
         * misura.
         */
        double passo = stepLength();
        if(ActionWeights.fisicaViva > 0){
            double reale = src.body.Palco.consuma(EntityID, passo);
            if(reale >= 0){
                passo = reale;
            }
        }
        double step = passo * sign;
        if(step == 0){
            // non si e' mosso di un centimetro: il tentativo e' comunque costato fiato
            spend(moveCost());
            return;
        }
        double tx = exactX + Math.sin(facing) * step;
        double tz = exactZ + Math.cos(facing) * step;
        int nx = cellOf(tx);
        int nz = cellOf(tz);
        if(nx < 0 || nx >= dim || nz < 0 || nz >= dim){
            Netreward -= 10;
            return;
        }
        if(world.isWaterColumn(nx, nz)){
            // L'acqua non si attraversa a nuoto: ci si puo' solo affacciare per bere
            Netreward -= 2;
            return;
        }
        if(Flora.isTrunk(nx, nz)){
            // Un albero e' un oggetto fisico: lo si gira attorno, non lo si attraversa. E' anche
            // il primo ostacolo solido del mondo, quindi il primo motivo per girarsi.
            Netreward -= 1;
            return;
        }
        if(!spend(moveCost())){ return; }
        /*
         * NIENTE RICOMPENSA DI AVVICINAMENTO. Qui c'era un gradiente che premiava il passo se
         * accorciava la distanza dal cibo o dall'acqua visibili. Era corretto in un punto (
         * guardava solo dentro il cono, quindi non premiava la fortuna) e sbagliato nel principio:
         * era un ORACOLO, diceva "quella direzione e' quella giusta" invece di lasciare che
         * fosse la vista a dirlo. E' il comportamento che vogliamo far emergere, quindi non si
         * puo' regalare. Si portava dietro anche i vecchi interruttori secchi della dieta, che
         * con l'abitudine alimentare non hanno piu' senso.
         *
         * Via anche il "+0.5 per un passo": camminare non e' buono in se'.
         */
        distanzaPercorsa += Math.abs(step);
        exactX = tx;
        exactZ = tz;
        // facing NON si tocca: la direzione e' uno stato che cambia solo girandosi (vedi turn).
        // Prima era l'effetto collaterale dell'ultimo spostamento, e un'entita' non poteva
        // guardare da una parte e camminare verso un'altra.
        gaitPhase += secondsPerAction();   // il ciclo dell'andatura avanza solo quando cammina
        lastMoveTick = Simulation.getTick();
        syncCell();
    }

    /** Mette l'entita' in una posizione continua (nascita, caricamento di un salvataggio). */
    void placeAt(double x, double z){
        this.exactX = x;
        this.exactZ = z;
        syncCell();
    }

    /** Allinea la cella (e la quota del terreno) alla posizione continua. */
    private void syncCell(){
        int nx = cellOf(exactX);
        int nz = cellOf(exactZ);
        position[0].set(nx);
        position[1].set(world.surfaceY(nx, nz)); // segue l'altezza del terreno
        position[2].set(nz);
    }

    private static int cellOf(double metres){
        return (int) Math.floor(metres / CELL_METERS);
    }

    /**
     * Costo in stamina di un passo: un corpo massiccio costa di piu' a spostare, e con il
     * metabolismo legato al tempo costa in proporzione alla STRADA che il passo copre.
     *
     * Senza la proporzione alla distanza un corpo con le gambe lunghe prende il doppio di strada
     * allo stesso prezzo: il vantaggio di camminare bene diventa energia gratis. Con essa, il
     * costo per metro dipende solo dalla massa (come nella realta') e camminare bene fa
     * risparmiare TEMPO - che adesso costa, perche' fame e sete scendono per tick.
     */
    private double moveCost(){
        double cost = MOVE_COST;
        if(ActionWeights.bodyMassStats > 0){
            cost *= getTraits().massFactor();
        }
        // il costo del passo e' proporzionale alla distanza coperta: il metabolismo va a
        // tempo, quindi camminare bene fa risparmiare tempo, e il tempo costa
        cost *= stepLength() / CELL_METERS;
        return cost;
    }

    // ------------------------------------------------------------------ sguardo

    /** Quanto la testa e' girata rispetto al corpo (radianti, dentro +/- gazeRange). */
    private volatile double gazeYaw;
    private volatile src.body.Gaze.Head head;

    /** Testa e portata del collo, dedotte dal corpo adulto (vedi src.body.Gaze). */
    public src.body.Gaze.Head getHead(){
        src.body.Gaze.Head h = head;
        if(h == null){
            h = src.body.Gaze.of(getAdultBody(), getTraits());
            head = h;
        }
        return h;
    }

    /** Quanto puo' girare la testa da un lato (radianti, 0 = collo rigido). */
    public double gazeRange(){
        return getHead().yawRange();
    }

    /** Quanto la testa e' girata rispetto al corpo (radianti). */
    public double getGazeYaw(){ return gazeYaw; }

    /**
     * Direzione in cui l'entita' sta GUARDANDO (radianti, come facing): corpo piu' testa. E' il
     * centro del cono di visuale.
     */
    public double getGazeDirection(){
        return Vision.wrap(facing + gazeYaw);
    }

    /**
     * Gira il corpo. Il cono di visuale segue, perche' e' centrato sullo sguardo.
     *
     * GIRARE E' LOCOMOZIONE, NON UN COMANDO. Prima questa riga era "facing += delta": una
     * rotazione gratuita e istantanea, cioe' la direzione era l'ultima cosa che il mondo
     * regalava alle entita' - e una regalia dallo stesso scaffale del rimontaggio dei corpi
     * caduti e del premio alla postura. Adesso, con la fisica viva, l'entita' CHIEDE di girare e
     * gira di quanto il suo corpo e' riuscito a girare davvero nel mondo fisico: un corpo che non
     * ha trovato nessuna svolta non gira, non riesce a puntare quello che vede, e muore.
     *
     * L'INTENZIONE SI SEGNA SUBITO, prima di sapere se la rotazione riuscira': e' lo stesso
     * circolo vizioso misurato il 24/10/2026 per il passo (legare il comando al risultato da'
     * zero per sempre). Il palco muove i giunti di chi CHIEDE di girare, non di chi ci e' gia'
     * riuscito.
     */
    private void turn(double delta){
        lastTurnWishTick = Simulation.getTick();
        lastTurnSign = Math.signum(delta);
        if(!spend(TURN_COST)){ return; }
        double giro = delta;
        if(ActionWeights.fisicaViva > 0){
            double reale = src.body.Palco.consumaImbardata(EntityID, Math.abs(delta));
            if(reale >= 0){
                giro = Math.signum(delta) * reale;
            }
        }
        facing = Vision.wrap(facing + giro);
    }

    /** Ultimo tick in cui ha SCELTO di girare, riuscendoci o no. Vedi turn(). */
    private volatile long lastTurnWishTick = Long.MIN_VALUE;
    /** Da che parte ha chiesto di girare: -1 a sinistra, +1 a destra. */
    private volatile double lastTurnSign = 0;

    /** Se sta PROVANDO a girare: lo usa il palco per sapere che sterzo dare al corpo. */
    public boolean vuoleGirare(){
        return Simulation.getTick() - lastTurnWishTick <= 2;
    }

    /**
     * Lo sterzo da dare al corpo: -1 a sinistra, +1 a destra, 0 dritto.
     *
     * E' una RICHIESTA. Quanto il corpo giri davvero dipende dalla svolta che ha trovato (vedi
     * src.body.Gait.sterzo), esattamente come quanto avanza dipende dal passo che ha trovato.
     */
    public double sterzoDelCorpo(){
        return vuoleGirare() ? lastTurnSign : 0.0;
    }

    /** Gira la testa, entro i limiti del collo: oltre, l'azione non fa niente. */
    private void turnHead(double delta){
        double range = gazeRange();
        if(range <= 1e-3){
            Netreward -= 0.5;   // collo rigido: l'azione e' sprecata
            return;
        }
        double want = gazeYaw + delta;
        gazeYaw = Math.max(-range, Math.min(range, want));
    }

    /** Posizione continua in metri (per grafica, portata e distanze reali). */
    public double getExactX(){ return exactX; }
    public double getExactZ(){ return exactZ; }
    /** Direzione verso cui guarda (radianti attorno a Y). */
    public double getFacing(){ return facing; }

    // ------------------------------------------------------------------ posa per la grafica

    /** Secondi di cammino accumulati: e' la fase del ciclo dell'andatura. */
    private volatile double gaitPhase;
    /**
     * I METRI DAVVERO PERCORSI IN VITA. Non la velocita' misurata dalla fisica su un pavimento
     * piatto: quanto terreno questo corpo ha coperto in questo mondo, con gli alberi in mezzo e
     * con il suo cervello a decidere dove andare. E' la prova che il registratore di movimento
     * conserva (vedi src.body.GaitArchive).
     */
    private volatile double distanzaPercorsa;

    /** Metri percorsi in tutta la vita. */
    public double getDistanzaPercorsa(){ return distanzaPercorsa; }
    private volatile long lastMoveTick = Long.MIN_VALUE;
    /** Ultimo tick in cui ha SCELTO di muoversi, riuscendoci o no (vedi vuoleMuoversi). */
    private volatile long lastWalkWishTick = Long.MIN_VALUE;

    public double getGaitPhase(){ return gaitPhase; }

    /** True se si e' mossa negli ultimi tick: la grafica anima gli arti solo mentre cammina. */
    public boolean isWalking(){
        return Simulation.getTick() - lastMoveTick <= 2;
    }

    /**
     * Se sta PROVANDO a camminare, che non e' la stessa cosa di {@link #isWalking()} (quello dice
     * se si e' mosso). Lo usa il palco per decidere se le gambe si muovono: un corpo che ci prova
     * e non riesce ad avanzare di un centimetro deve continuare a muovere le gambe, altrimenti
     * non imparera' mai.
     */
    public boolean vuoleMuoversi(){
        return Simulation.getTick() - lastWalkWishTick <= 2;
    }

    /**
     * In che stato questa entita' sta tenendo il proprio corpo, per la fisica viva (vedi
     * src.body.Palco.Stato). Dormire non e' "stare ferma": e' non dare piu' comandi al corpo.
     */
    public src.body.Palco.Stato statoDelCorpo(){
        if(isSleeping()){
            return src.body.Palco.Stato.DORME;
        }
        /*
         * CHI VUOLE GIRARE STA MUOVENDO IL CORPO quanto chi vuole camminare: girare e'
         * locomozione, e un corpo con i motori che tengono la posa non gira. Senza questo, lo
         * sterzo arriverebbe a un corpo con la fase ferma, cioe' a un corpo che non sta facendo
         * niente, e nessuna entita' riuscirebbe mai a cambiare direzione.
         */
        return vuoleMuoversi() || vuoleGirare()
                ? src.body.Palco.Stato.CAMMINA : src.body.Palco.Stato.FERMA;
    }

    /**
     * Posa del corpo nel suo sistema locale: origine sotto i piedi (y = 0 al terreno, x = z = 0
     * al centro dell'entita'), girata nella direzione in cui guarda e con gli angoli dei giunti
     * presi dall'andatura al punto del ciclo in cui si trova.
     *
     * La grafica la traduce nel mondo con una sola traslazione, cosi' la simulazione non deve
     * sapere nulla di come e' disegnato il terreno. extraSeconds serve al motore grafico per
     * interpolare tra due tick e avere un movimento fluido anche a velocita' bassa.
     */
    public src.body.BodyPose poseLocal(double extraSeconds){
        /*
         * SE QUESTA ENTITA' E' IN FISICA VIVA, LA POSA E' IL CORPO FISICO. Non gli angoli della
         * fisica rimessi dentro la cinematica diretta - quella era una ricreazione del movimento,
         * e si vedeva un corpo in piedi mentre nel mondo fisico strisciava. Qui si prende la
         * trasformata di ogni pezzo come sta in Jolt: se si trascina con un arto si vede
         * trascinarsi, se cade in continuazione si vede cadere, se dorme per terra sta per terra.
         * L'unica cosa che non viene dalla fisica e' l'imbardata, che appartiene alla simulazione
         * (vedi Palco.posaDi).
         */
        if(ActionWeights.fisicaViva > 0){
            src.body.BodyPose fisica = src.body.Palco.posaDi(EntityID, facing);
            if(fisica != null){
                return fisica;
            }
        }
        src.body.Automaton b = getBody();
        double[] q = jointAngles(b, extraSeconds);
        src.body.Rot rot = src.body.Rot.yaw(facing);
        // prima posa all'origine per sapere quanto e' alto il punto piu' basso, poi si rialza
        // di quel tanto: cosi' i piedi (o la pancia, per chi striscia) toccano terra
        src.body.BodyPose at0 = b.pose(q, src.body.V3.ZERO, rot);
        return b.pose(q, new src.body.V3(0, -at0.lowestPoint(), 0), rot);
    }

    /**
     * Angoli dei giunti: andatura in movimento, posizione di riposo da ferma, e in ogni caso la
     * testa girata dove l'entita' sta guardando - cosi' sullo schermo si VEDE dove guarda, e la
     * posa disegnata e' la stessa cosa che il cervello percepisce.
     */
    private double[] jointAngles(src.body.Automaton b, double extraSeconds){
        /*
         * QUESTA E' LA STRADA PER CHI NON E' IN FISICA VIVA: il ciclo dell'andatura, disegnato.
         * Per chi e' in fisica la posa arriva intera dal mondo fisico e non passa da qui (vedi
         * poseLocal), proprio per non avere due modi di ottenere la stessa cosa.
         */
        double[] q = new double[b.dofCount()];
        if(ActionWeights.bodyGaitSpeed > 0 && isWalking()){
            float[] f = getGait().anglesAt(b, gaitPhase + extraSeconds, null);
            for(int i = 0; i < q.length && i < f.length; i++){
                q[i] = f[i];
            }
        }
        applyGaze(q);
        return q;
    }

    /**
     * Distribuisce la rotazione dello sguardo sui giunti del collo, in proporzione a quanto
     * ognuno puo' girare (vedi src.body.Gaze). Si somma all'andatura invece di sostituirla: il
     * collo di chi cammina oscilla e intanto guarda.
     */
    private void applyGaze(double[] q){
        src.body.Gaze.Head h = getHead();
        if(!h.canTurn()){
            return;
        }
        for(src.body.Gaze.YawDof d : h.yawDofs()){
            if(d.index() < q.length){
                double a = q[d.index()] + gazeYaw * d.share();
                q[d.index()] = Math.max(-d.limit(), Math.min(d.limit(), a));
            }
        }
    }

    /**
     * Quanto lontano vede ADESSO (celle): la propria vista ridotta dal buio e recuperata dalla
     * visione notturna. Da usare al posto di stats.sightDistance in tutto cio' che percepisce,
     * altrimenti il ciclo del giorno non cambia niente per nessuno.
     */
    public double sightNow(){
        return stats.sightDistance * DayNight.visibility(stats.nightVision);
    }

    /**
     * A che altezza arriva una presa, in metri sopra i propri piedi: il corpo in piedi piu' le
     * braccia alzate. E' questo numero a decidere se una mela sulla chioma e' raccoglibile
     * (vedi Flora.pickFrom).
     *
     * Qui sta l'unico vantaggio puro dell'essere grandi in tutto il progetto. Tutto il resto
     * della taglia e' un costo: piu' massa = passo piu' caro, metabolismo piu' caro. Senza un
     * posto in cui l'altezza PAGHI, la selezione spinge sempre verso corpi piccoli e il corpo
     * evoluto diventa una questione di una sola dimensione.
     */
    public double pickHeight(){
        src.body.BodyTraits t = getTraits();
        return (t.height() + t.reach()) * src.body.BodyParams.growthFactor(age);
    }

    /** Portata delle estremita' alla taglia attuale (m): quanto lontano arriva un colpo o una presa. */
    public double reachNow(){
        return getTraits().reach() * src.body.BodyParams.growthFactor(age);
    }

    /** Distanza orizzontale in metri da un'altra entita'. */
    public double distanceTo(entity other){
        double dx = other.exactX - exactX;
        double dz = other.exactZ - exactZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------------ bere

    /** Si beve se si e' adiacenti (stessa cella o cella affianco) a una colonna d'acqua. */
    private void drink(){
        int x = position[0].get();
        int y = position[1].get();
        int z = position[2].get();
        boolean nearWater = false;
        for(int dx = -1; dx <= 1 && !nearWater; dx++){
            for(int dz = -1; dz <= 1; dz++){
                int wx = x + dx, wz = z + dz;
                if(wx < 0 || wx >= world.getDim() || wz < 0 || wz >= world.getDim()){ continue; }
                if(world.isWaterColumn(wx, wz)){ nearWater = true; break; }
            }
        }
        if(!nearWater){
            Netreward -= 1.5;
            return;
        }
        if(!spend(1)){ return; }
        int old = thirst;
        thirst = Math.min(MAX_THIRST, thirst + 60);
        if(thirst != old){
            Netreward += ActionWeights.drinkNeedScaled > 0
                    ? ActionWeights.drinkReward * (1.0 - old / (double) MAX_THIRST) : ActionWeights.drinkReward;
        }
    }

    // ------------------------------------------------------------------ mangiare

    /** Mezza diagonale di una cella: la distanza entro cui si raggiunge cio' che si ha sotto i piedi. */
    private static final double SAME_CELL_RANGE = 0.75;

    /**
     * Da che distanza si riesce a prendere il cibo: con gli effetti del corpo attivi e' la
     * portata delle estremita' (braccia lunghe = si raccoglie una mela dalla cella accanto senza
     * spostarsi), altrimenti solo la propria cella come prima.
     */
    private double eatRange(){
        return ActionWeights.bodyReach > 0 ? Math.max(SAME_CELL_RANGE, reachNow()) : SAME_CELL_RANGE;
    }

    /** Distanza dal centro di una cella. */
    private double distanceToCell(int x, int z){
        double dx = (x + 0.5) * CELL_METERS - exactX;
        double dz = (z + 0.5) * CELL_METERS - exactZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Cella con la mela piu' vicina entro la portata, oppure null. La mela sta sulla superficie
     * della sua colonna, quindi si cerca alla quota di QUELLA colonna e si accetta se il
     * dislivello rispetto ai propri piedi sta entro ActionWeights.levelTolerance: una mela a una
     * cella di distanza su un gradino alto 30 cm si raccoglie, com'e' ovvio.
     */
    private Flora.Apple appleWithinReach(){
        // NESSUN FILTRO DI DIETA: chiunque puo' prendere qualunque cibo. Quanto gli rende lo
        // decide appleEfficiency(), cioe' la sua abitudine - un divieto non e' una pressione.
        int y = position[1].get();
        int cx = position[0].get();
        int cz = position[2].get();
        double height = pickHeight();
        if(ActionWeights.bodyReach <= 0){
            return Flora.pickFrom(cx, cz, y, height);
        }
        double range = eatRange();
        int r = (int) Math.ceil(range);
        int dim = world.getDim();
        Flora.Apple best = null;
        double bestValue = -1;
        for(int x = cx - r; x <= cx + r; x++){
            if(x < 0 || x >= dim){ continue; }
            for(int z = cz - r; z <= cz + r; z++){
                if(z < 0 || z >= dim){ continue; }
                double d = distanceToCell(x, z);
                if(d > range){ continue; }
                if(!percepibile(x, z)){ continue; }   // non la vedo, non la raccolgo
                Flora.Apple a = Flora.pickFrom(x, z, y, height);
                if(a == null){ continue; }
                // a parita' di distanza vince la mela piu' matura, e una mela matura vale la pena
                // di un passo in piu': il valore cala con la distanza invece di escluderla
                double value = a.nutrition() - 0.05 * d;
                if(value > bestValue){
                    bestValue = value;
                    best = a;
                }
            }
        }
        return best;
    }

    /**
     * LA MASCHERA NON DEVE RIVELARE QUELLO CHE NON SI VEDE.
     *
     * Con ActionWeights.maskActions la rete scegliera' solo tra le azioni che possono riuscire:
     * la maschera e' quindi INFORMAZIONE, esattamente come la vista. Finche' "posso bere" era
     * vero per una pozza in una qualunque delle otto celle intorno, un'entita' sapeva dell'acqua
     * che aveva di fianco senza averla mai guardata - cioe' le otto celle erano state togliute
     * dalla vista e sopravvivevano qui, che e' lo stesso difetto in un posto meno visibile.
     * Lo stesso valeva per il cibo a portata e per il partner (findPartnerNoRandom cercava in un
     * disco di raggio 3 a 360 gradi).
     *
     * Adesso un bersaglio e' PERCEPIBILE se sta sotto i piedi (quello e' tatto, e si sente a
     * occhi chiusi) oppure dentro il cono dello sguardo. Si guarda solo l'angolo e non la
     * distanza, perche' qui si parla sempre di roba a portata di braccio.
     *
     * La REALTA' FISICA non cambia: drink() beve ancora da una cella adiacente e attack()
     * colpisce chi ha davanti. Quello che cambia e' che l'entita' non viene piu' avvisata.
     */
    private boolean percepibile(int x, int z){
        if(ActionWeights.maskSeen <= 0){ return true; }
        int dx = x - position[0].get();
        int dz = z - position[2].get();
        /*
         * IL TATTO ARRIVA A CIO' CHE TI STA ACCANTO, non solo sotto i piedi.
         *
         * Nella prima versione il tatto era la sola cella propria, e il risultato misurato su
         * dodici semi appaiati e' stato un disastro: nascite 87 contro 256 (p = 0.039) e CINQUE
         * CORSE ESTINTE su dodici contro zero. Il motivo e' che chiedeva di FISSARE l'acqua che
         * si ha sotto il braccio (drink beve dalle celle adiacenti) e la compagna a una cella di
         * distanza: non e' percezione realistica, e' una penalita'.
         *
         * Con maskSeen = 2 il contatto copre le celle adiacenti - cioe' esattamente la portata
         * con cui si bere e si incontra qualcuno - e il cono serve per tutto il resto, che e' il
         * punto: di una cosa lontana non si puo' sapere niente senza averla guardata.
         */
        if(Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && ActionWeights.maskSeen >= 2){ return true; }
        if(dx == 0 && dz == 0){ return true; }
        return Vision.offAxis(dx, dz, getGazeDirection(),
                Math.toRadians(stats.fieldOfView) / 2.0) >= 0;
    }

    /** True se un oggetto alla quota "other" e' raggiungibile da chi ha i piedi alla quota "mine". */
    static boolean reachableLevel(int mine, int other){
        return Math.abs(mine - other) <= (int) ActionWeights.levelTolerance;
    }

    /** Cadavere piu' vicino entro la portata (allo stesso livello), oppure null. */
    private Corpse corpseWithinReach(){
        int y = position[1].get();
        if(ActionWeights.bodyReach <= 0){
            return Simulation.corpseAt(position[0].get(), y, position[2].get());
        }
        double range = eatRange();
        Corpse best = null;
        double bestD = Double.MAX_VALUE;
        for(Corpse c : Simulation.corpsesSnapshot()){
            if(c.isConsumed() || !reachableLevel(y, c.getY())){ continue; }
            double d = distanceToCell(c.getX(), c.getZ());
            if(!percepibile(c.getX(), c.getZ())){ continue; }
            if(d <= range && d < bestD){
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    private void eat(){
        Flora.Apple apple = appleWithinReach();
        Corpse corpse = corpseWithinReach();

        // Con entrambi a portata si prende quello che SAZIA PIU' ME: la stessa carogna conviene a
        // chi ha l'abitudine alla carne e non conviene a chi non ce l'ha. La preferenza non e'
        // piu' una regola per categoria, e' il conto del nutrimento atteso.
        boolean carneMeglio = corpse != null && apple != null
                && BITE_MEAT * meatEfficiency() > 100 * appleEfficiency() * apple.nutrition();
        if(corpse != null && (apple == null || carneMeglio)){
            eatCorpse(corpse);
        }else if(apple != null){
            eatApple(apple);
        }else{
            Netreward -= 2.0;
        }
    }

    /**
     * Quanto rende un cibo per cui si e' specializzati per una quota x (0 = non e' affatto il mio
     * cibo, 1 = e' esattamente il mio cibo). Lineare tra dietEffMin e dietEffMax.
     *
     * Con 0.25 e 1.35 il centro cade esattamente su 0.80, cioe' sull'onnivoro di prima: il
     * cambiamento non sposta la media del mondo, allarga solo gli estremi - e li rende
     * raggiungibili per esperienza invece che per sorteggio alla nascita.
     */
    private static double resa(double x){
        double min = ActionWeights.dietEffMin;
        return min + (ActionWeights.dietEffMax - min) * Math.max(0.0, Math.min(1.0, x));
    }

    private double appleEfficiency(){ return resa(1.0 - dietBias); }

    private double meatEfficiency(){ return resa(dietBias); }

    /** Un pasto sposta l'abitudine verso quello che si e' mangiato (0 = vegetale, 1 = carne). */
    private void adattaDieta(double bersaglio){
        dietBias += ActionWeights.dietAdapt * (bersaglio - dietBias);
        if(dietBias < 0){ dietBias = 0; }
        if(dietBias > 1){ dietBias = 1; }
    }

    public double getDietBias(){ return dietBias; }

    /**
     * L'etichetta della dieta ADESSO, calcolata dall'abitudine corrente e non dal gene: e' quella
     * che si mostra nella GUI e si conta nei log, e nel corso di una vita puo' cambiare.
     */
    public DietType dietNow(){ return DietType.fromResolvedBias(dietBias); }

    /**
     * Mangia la mela. Il cibo che da' dipende dalla sua MATURAZIONE (Flora.Apple.nutrition): una
     * verde sfama un quarto di una matura, una passata la meta'. Quindi la ricompensa che la rete
     * riceve non premia "mangiare" ma "mangiare qualcosa che vale", che e' la differenza tra
     * ripulire una chioma di frutti verdi e aspettare.
     */
    private void eatApple(Flora.Apple a){
        int oldFood = food;
        double value = Flora.take(a);
        food += (int) Math.round(100 * stats.foodAbsorption * appleEfficiency() * value);
        if(food > maxFood){ food = maxFood; }
        consumedFood++;
        adattaDieta(0.0);   // mangiare vegetale sposta l'abitudine verso il vegetale
        if(oldFood != food){
            // in proporzione al cibo DAVVERO ricavato: con l'abitudine alimentare la stessa mela
            // sazia molto un erbivoro e quasi niente un carnivoro, e la ricompensa lo dice
            Netreward += ActionWeights.eatApple * (food - oldFood) / 100.0;
            applesEaten++; // conta solo le mele che hanno davvero dato cibo
        }
    }

    private void eatCorpse(Corpse corpse){
        int oldFood = food;
        double bite = corpse.takeBite(BITE_MEAT);
        food += (int) Math.round(bite * stats.foodAbsorption * meatEfficiency());
        if(food > maxFood){ food = maxFood; }
        consumedFood++;
        adattaDieta(1.0);   // mangiare carne sposta l'abitudine verso la carne
        // Il cibo ottenuto dal corpo e' cio' che si premia (non l'attacco in se')
        if(oldFood != food){ Netreward += ActionWeights.eatCorpse * (food - oldFood) / 100.0; }
    }

    // ------------------------------------------------------------------ combattimento

    void takeDamage(int amount, String cause){
        healt -= amount;
        lastDamageCause = cause;
        Netreward -= amount * 0.2; // si punisce la salute persa
    }

    /** Distanza entro cui un colpo arriva: la portata delle estremita', almeno una cella. */
    private double attackRange(){
        return Math.max(CELL_METERS, reachNow());
    }

    /**
     * Quanto conta il corpo nel danno: la massa (inerzia del colpo) e la portata (leva). Entrambe
     * compresse, cosi' un corpo enorme con braccia lunghe colpisce circa il doppio di uno medio,
     * non dieci volte tanto.
     */
    private double attackFactor(){
        if(ActionWeights.bodyAttackPower <= 0){
            return 1.0;
        }
        src.body.BodyTraits t = getTraits();
        return Math.sqrt(t.massFactor()) * (0.7 + 0.3 * t.reachFactor());
    }

    private void attack(){
        int x = position[0].get();
        int y = position[1].get();
        int z = position[2].get();
        entity target = null;
        double range = attackRange();
        /*
         * SI COLPISCE QUELLO CHE SI HA INQUADRATO, non il piu' debole a portata.
         *
         * Prima il bersaglio era "il piu' debole entro un passo", a 360 gradi: un'entita'
         * ammazzava a occhi chiusi i cuccioli che le passavano dietro - compresi i propri figli e
         * la propria compagna, senza averli nemmeno visti. Nella sessione 19 sono 178 morti
         * ammazzate contro 127 di fame, cioe' il 57% delle morti, con la salute media che SALIVA:
         * non era predazione, era una falciatrice.
         *
         * Adesso il bersaglio deve stare nel cono dello sguardo (corpo + testa, lo stesso cono di
         * Vision) e si prende il piu' CENTRATO, a parita' il piu' vicino. Non serve nessuna
         * regola sulla parentela e nessuna difesa obbligatoria: chi mena alla cieca colpisce il
         * vuoto, e il colpo a vuoto costa gia' sei di stamina e un punto di ricompensa. Il
         * prezzo dell'aggressione esce dalla geometria, non da un divieto.
         */
        double gaze = getGazeDirection();
        double half = Math.toRadians(stats.fieldOfView) / 2.0;
        double bestScarto = Double.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        for(entity o : Entity_manager.snapshotAlive()){
            if(o == this || !o.isAlive()){ continue; }
            int[] p = o.getPos();
            // ERA "p[1] != y", cioe' solo chi sta ESATTAMENTE alla propria quota. Vista,
            // cadaveri e ricerca del partner passano tutti da reachableLevel da quando il
            // terreno a gradini e' stato sistemato (vedi STATO_LAVORO voce 12): l'attacco era
            // rimasto indietro, e un'entita' su un gradino alto 30 cm era intoccabile.
            if(!reachableLevel(y, p[1])){ continue; }
            double d;
            if(ActionWeights.bodyReach > 0){
                d = distanceTo(o);
                if(d > range){ continue; }
            }else{
                if(Math.abs(p[0] - x) > 1 || Math.abs(p[2] - z) > 1){ continue; }
                d = distanceToCell(p[0], p[2]);
            }
            double scarto = Vision.offAxis(p[0] - x, p[2] - z, gaze, half);
            if(scarto < 0){ continue; }   // fuori dal cono: non lo vedo, non lo colpisco
            if(scarto < bestScarto || (scarto == bestScarto && d < bestDist)){
                bestScarto = scarto;
                bestDist = d;
                target = o;
            }
        }
        if(target == null){
            Netreward -= 1.0; // colpo a vuoto
            return;
        }
        int damage = Math.max(1, (int) Math.round(stats.attackPower * attackFactor() * (0.8 + 0.4 * rng().nextDouble())));
        String cause = "ucciso da #" + EntityID;
        target.takeDamage(damage, cause);
        // Nessuna ricompensa per il colpo: conta solo il cibo che si ricava dal corpo
        if(target.healt <= 0){
            kills++;
            Simulation.kill(target, cause);
        }
    }

    // ------------------------------------------------------------------ riposo

    private void sleep(){
        sleeping = true;
        double need = 1.0 - stamina / stats.maxStamina;
        stamina = Math.min(stats.maxStamina, stamina + stats.maxStamina * SLEEP_REGEN_FRACTION);
        if(need < 0.1){
            Netreward += ActionWeights.sleepRestedPenalty; // dormire da riposato non serve
        }else{
            Netreward += need * ActionWeights.sleepNeedFactor;
        }
    }

    // ------------------------------------------------------------------ riproduzione

    private boolean canMate(){
        return isAlive() && age >= MATURITY_AGE && reproCooldown == 0 && food >= REPRO_PARTNER_FOOD_MIN;
    }

    private entity findPartner(){
        int x = position[0].get();
        int y = position[1].get();
        int z = position[2].get();
        java.util.ArrayList<entity> candidates = new java.util.ArrayList<>();
        double range = partnerRange();
        for(entity o : Entity_manager.snapshotAlive()){
            if(o == this || o.sex == this.sex || !o.canMate()){ continue; }
            int[] op = o.getPos();
            // STESSO FILTRO DELLA MASCHERA (findPartnerNoRandom): le due ricerche devono dire la
            // stessa cosa, altrimenti la maschera promette un figlio e reproduce() non lo fa, o
            // peggio il contrario - un partner che non si percepisce.
            if(reachableLevel(y, op[1]) && distanceTo(o) <= range
                    && percepibile(op[0], op[2])){
                candidates.add(o);
            }
        }
        if(candidates.isEmpty()){ return null; }
        return candidates.get(rng().nextInt(candidates.size()));
    }

    /** Due genitori: l'entita' che sceglie di riprodursi cerca un partner vicino di sesso opposto. */
    /**
     * Chi mi ha chiesto di accoppiarsi, se la richiesta e' ancora fresca, se e' ancora viva, se
     * puo' ancora farlo e se la percepisco. Null in tutti gli altri casi.
     *
     * La richiesta non e' un contratto: tra la domanda e la risposta l'altra puo' essere morta,
     * aver mangiato l'ultima mela o essersi allontanata. Si ricontrolla tutto al momento del si'.
     */
    private entity richiedente(){
        if(richiestaDa < 0 || Simulation.getTick() - richiestaTick > RICHIESTA_TTL){
            return null;
        }
        for(entity o : Entity_manager.snapshotAlive()){
            if(o.getId() != richiestaDa){ continue; }
            int[] op = o.getPos();
            if(o.sex == this.sex || !o.canMate()){ return null; }
            if(!reachableLevel(position[1].get(), op[1])){ return null; }
            if(distanceTo(o) > partnerRange()){ return null; }
            if(!percepibile(op[0], op[2])){ return null; }
            return o;
        }
        return null;
    }

    /**
     * CHIEDO. Il bersaglio e' il partner possibile piu' CENTRATO nel cono (a parita' il piu'
     * vicino): si chiede a chi si sta guardando, come si colpisce chi si sta guardando.
     * Chiedere non fa nascere niente: mette la richiesta in capo all'altra e accende il proprio
     * segnale. Se non c'e' nessuno a cui chiedere, la domanda va a vuoto e costa come un colpo
     * a vuoto.
     */
    private void chiedi(){
        int y = position[1].get();
        double gaze = getGazeDirection();
        double half = Math.toRadians(stats.fieldOfView) / 2.0;
        entity scelta = null;
        double bestScarto = Double.MAX_VALUE, bestD = Double.MAX_VALUE;
        for(entity o : Entity_manager.snapshotAlive()){
            if(o == this || o.sex == this.sex || !o.canMate()){ continue; }
            int[] op = o.getPos();
            if(!reachableLevel(y, op[1])){ continue; }
            double d = distanceTo(o);
            if(d > partnerRange() || !percepibile(op[0], op[2])){ continue; }
            double scarto = Vision.offAxis(op[0] - position[0].get(), op[2] - position[2].get(),
                    gaze, half);
            if(scarto < 0){ scarto = 0; }   // e' a contatto: conta come inquadrata
            if(scarto < bestScarto || (scarto == bestScarto && d < bestD)){
                bestScarto = scarto;
                bestD = d;
                scelta = o;
            }
        }
        if(scelta == null){
            Netreward -= 1.0;
            return;
        }
        if(!spend(RICHIESTA_COST)){ return; }
        scelta.richiestaDa = this.EntityID;
        scelta.richiestaTick = Simulation.getTick();
        this.haChiestoTick = Simulation.getTick();
    }

    /** ACCETTO: da qui in poi e' il vecchio accoppiamento, con il richiedente come partner. */
    private void accetta(entity chiedente){
        chiedente.haChiestoTick = Long.MIN_VALUE;   // la domanda ha avuto risposta
        this.richiestaDa = -1;
        // chi ha chiesto e' il primo genitore, chi accetta il secondo: il figlio nasce dov'e'
        // chi ha accettato, e la ricompensa piu' grande va a chi si e' mosso per primo
        chiedente.generaFiglioCon(this);
    }

    private void reproduce(){
        if(age < MATURITY_AGE || reproCooldown > 0 || food < REPRO_FOOD_MIN
                || Entity_manager.Entity_count() >= Simulation.maxPopulation()){
            Netreward -= 1.0;
            return;
        }
        entity partner = findPartner();
        if(partner == null){
            Netreward -= 1.0;
            return;
        }
        generaFiglioCon(partner);
    }

    /**
     * Il figlio di due genitori, dato che sono d'accordo: il cuore dell'accoppiamento, chiamato
     * sia dal vecchio modo immediato sia dal protocollo richiesta-risposta.
     */
    private void generaFiglioCon(entity partner){
        // Tra la domanda e il si' passano fino a RICHIESTA_TTL tick: le condizioni si ricontrollano
        // qui, che e' l'unico punto da cui nasce un figlio in tutti e due i modi.
        if(!canMate() || !partner.canMate()
                || Entity_manager.Entity_count() >= Simulation.maxPopulation()){
            Netreward -= 1.0;
            return;
        }
        if(!spend(REPRODUCE_COST)){ return; }

        int id = Entity_manager.newId();
        Genome childGenome = Genome.reproduce(this.genome, partner.genome, SCHEMA, rng(),
                String.valueOf(id), Simulation.getTick());
        entity child = new entity(id, world.getCycle(),
                position[0].get(), position[1].get(), position[2].get(), childGenome);

        // Mutazione adattiva: chi va molto meglio della media esplora meno
        double avgFitness = Entity_manager.getAverageFitness();
        double rate = getFitness() > avgFitness * 1.2 ? 0.05 : 0.2;
        child.inheritBrain(Reproduction.crossover(this.bestBrain, partner.bestBrain, rate));
        // L'andatura si eredita come il cervello: incrocio dei due genitori piu' una piccola
        // mutazione. Cosi' il controllore evolve INSIEME al corpo, e un figlio con una gamba in
        // piu' parte dall'andatura dei genitori sui giunti che ha ereditato.
        child.inheritGait(src.body.Gait.cross(this.getGait(), partner.getGait(), child.getAdultBody(), rng())
                .perturb(child.getAdultBody(), rng(), GAIT_MUTATION));
        child.placeAt(exactX, exactZ);
        child.food = 120;

        this.food -= REPRO_FOOD_COST;
        partner.food -= REPRO_FOOD_COST;
        this.reproCooldown = REPRO_COOLDOWN;
        partner.reproCooldown = REPRO_COOLDOWN;
        this.Netreward += ActionWeights.reproduceSelf;
        partner.Netreward += ActionWeights.reproducePartner;

        Simulation.queueBirth(child);
        Genealogy.recordBirth(child, this, partner);
        System.out.println("Nascita ID "+id+" da "+EntityID+" e "+partner.getId()
                +" (generazione "+childGenome.lineage().generation()+")");
    }

    // ------------------------------------------------------------------ genoma

    /**
     * Salute massima: il gene stat.max_health dice la "costituzione", la massa del corpo la
     * scala (compressa e limitata, vedi BodyTraits.massFactor). Un corpo piu' grosso regge piu'
     * colpi, ma paga in costo del movimento e in velocita'.
     */
    private int maxHealthInt(){
        double base = stats.maxHealth;
        if(ActionWeights.bodyMassStats > 0){
            base *= getTraits().massFactor();
        }
        return (int) Math.max(1, Math.round(base));
    }

    /** Salute massima effettiva (per diagnostica e per il motore grafico). */
    public int getMaxHealth(){
        return maxHealthInt();
    }

    /** Il genoma si modifica con l'eta': deriva somatica periodica, poi le stat vengono ricalcolate. */
    private void applyAgeDrift(){
        if(age % AGE_DRIFT_INTERVAL != 0){ return; }
        double intensity = Math.min(1.0, age / AGE_DRIFT_REFERENCE);
        Genome drifted = genome.ageDrift(SCHEMA, rng(), intensity);
        genome = drifted;
        stats = EntityStats.resolve(drifted, SCHEMA);
        if(healt > maxHealthInt()){ healt = maxHealthInt(); }
        if(stamina > stats.maxStamina){ stamina = stats.maxStamina; }
    }

    public void DayPass(int tick){
        if(tick%(100*Math.max(1, born))==0){
            born++;
        }
    }

}
