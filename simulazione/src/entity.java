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
 * Azioni scelte dalla rete neurale (uscite): idle, 4 movimenti, mangia, attacca, dormi,
 * riproduci. Ogni azione (tranne idle e dormi) consuma stamina; la stamina si rigenera solo
 * dormendo. Le caratteristiche vitali derivano dal genoma (EntityStats).
 */
public class entity {

    // --- Rete neurale: input e output FISSI per tutte le entita' ---
    public static final int SELF_INPUTS = 8;
    public static final int INPUT_SIZE = Vision.SIZE + SELF_INPUTS;
    public static final int OUTPUT_SIZE = 10;
    public static final int[] BRAIN_TOPOLOGY = {INPUT_SIZE, 32, 16, OUTPUT_SIZE};

    public static final int ACT_IDLE = 0;
    public static final int ACT_MOVE_XP = 1;
    public static final int ACT_MOVE_XM = 2;
    public static final int ACT_MOVE_ZP = 3;
    public static final int ACT_MOVE_ZM = 4;
    public static final int ACT_EAT = 5;
    public static final int ACT_ATTACK = 6;
    public static final int ACT_SLEEP = 7;
    public static final int ACT_REPRODUCE = 8;
    public static final int ACT_DRINK = 9;

    // OUTPUT_SIZE aggiornato per includere "bevi"
    // (vedi sopra: BRAIN_TOPOLOGY usa OUTPUT_SIZE, quindi basta cambiare la costante)

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
    private static final double EXPLORATION = 0.15;      // probabilita' di azione casuale

    private static final double MOVE_COST = 2;
    private static final double EAT_COST = 1;
    private static final double ATTACK_COST = 6;
    private static final double REPRODUCE_COST = 8;
    private static final double SLEEP_REGEN_FRACTION = 0.15; // della stamina massima per passo di sonno

    private static final int REPRO_FOOD_MIN = 80;
    private static final int REPRO_PARTNER_FOOD_MIN = 60;
    private static final int REPRO_FOOD_COST = 40;
    private static final int REPRO_COOLDOWN = 40;
    private static final double BITE_MEAT = 60;
    private static final int PARTNER_RANGE = 3;          // celle entro cui un partner puo' essere raggiunto

    private static final int AGE_DRIFT_INTERVAL = 50;
    private static final double AGE_DRIFT_REFERENCE = 500.0;

    private static final int WINDOW_SIZE = 10;           // ogni quanti passi valuti la mutazione del cervello
    private static final double MUTATION_RATE = 0.25;

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
    private double Netreward = 0;
    private double actionPoints = 0;
    private boolean sleeping = false;
    private String lastDamageCause = "fame";
    private String deathCause = "";
    private long deathTick = -1;
    private AtomicInteger[] position = new AtomicInteger[3];
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
        this.healt = (int) Math.round(stats.maxHealth);
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
        o.put("netreward", Netreward);
        o.put("ticks", tick.get());
        JSONArray pos = new JSONArray();
        pos.put(position[0].get());
        pos.put(position[1].get());
        pos.put(position[2].get());
        o.put("pos", pos);
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
        e.Netreward = o.optDouble("netreward", 0);
        e.tick.set(o.optInt("ticks", 0));

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
        if(!Arrays.equals(n.getTopology(), BRAIN_TOPOLOGY)){
            System.out.println("Cervello salvato con topologia diversa "+Arrays.toString(n.getTopology())
                    +": ne uso uno nuovo.");
            return null;
        }
        return n;
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
        int aBase = Vision.CAT_APPLE * Vision.SECTORS;
        int wBase = Vision.CAT_WATER * Vision.SECTORS;
        int eBase = Vision.CAT_ENTITY * Vision.SECTORS;
        int hereBase = Vision.SECTORS * Vision.CATEGORIES;
        int self = Vision.SIZE;
        int hereApple = hereBase + Vision.CAT_APPLE;
        int hereCorpse = hereBase + Vision.CAT_CORPSE;
        int inFood = self + 1, inThirst = self + 2, inStamina = self + 3, inMature = self + 6;

        // Strato 1: 9 neuroni dedicati (4 direzioni verso mele/acqua, mangia, bevi, dormi, riproduci)
        int[][] moveSectors = {{3, 4}, {7, 0}, {5, 6}, {1, 2}}; // +x, -x, +z, -z
        for(int d = 0; d < 4; d++){
            double[] w = new double[INPUT_SIZE];
            for(int s : moveSectors[d]){
                w[aBase + s] = 6;
                w[wBase + s] = 6;
            }
            setNeuron(0, d, w, -1);
        }
        double[] eat = new double[INPUT_SIZE];
        eat[hereApple] = 8;
        eat[hereCorpse] = 8;
        setNeuron(0, 4, eat, -4);
        double[] drinkN = new double[INPUT_SIZE];
        for(int s = 0; s < Vision.SECTORS; s++){ drinkN[wBase + s] = 6; }
        drinkN[inThirst] = -6;
        setNeuron(0, 5, drinkN, -1);
        double[] rest = new double[INPUT_SIZE];
        rest[inStamina] = -8;
        setNeuron(0, 6, rest, 3);
        double[] mate = new double[INPUT_SIZE];
        mate[inFood] = 4;
        mate[inMature] = 4;
        for(int s = 0; s < Vision.SECTORS; s++){ mate[eBase + s] = 2; }
        setNeuron(0, 7, mate, -9);

        // Strato 2: ogni neurone dedicato ripete il corrispondente dello strato 1
        for(int j = 0; j < 8; j++){
            double[] w = new double[BRAIN_TOPOLOGY[1]];
            w[j] = 8;
            setNeuron(1, j, w, j < 4 ? -2 : -4);
        }

        // Uscite: mosse (1-4), mangia (5), dormi (7), riproduci (8), bevi (9)
        int[] outputs = {1, 2, 3, 4, 5, 7, 8, 9};
        for(int j = 0; j < outputs.length; j++){
            double[] w = new double[BRAIN_TOPOLOGY[2]];
            w[j] = j < 4 ? 10 : 12;
            setNeuron(2, outputs[j], w, -6);
        }
        bestBrain = Brain.copy();
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
        return input;
    }

    // ------------------------------------------------------------------ ciclo di vita

    /** Chiamato dal ciclo centrale a ogni tick: accumula punti azione e agisce quando arriva a 1. */
    public void advance(){
        actionPoints += stats.speed / SPEED_DIVISOR;
        if(actionPoints >= 1.0){
            actionPoints -= 1.0;
            step();
        }
    }

    private void step(){
        if(!life.get()){ return; }
        tick.addAndGet(1);
        age++;
        applyAgeDrift();
        if(reproCooldown > 0){ reproCooldown--; }
        sleeping = false;
        Netreward += 0.5; // un passo sopravvissuto

        double[] output = Brain.predict(get_NetInput());
        perform(chooseAction(output));
        if(!life.get()){ return; }

        // Fame, sete e salute (dormire consuma meta' del cibo, non consuma sete)
        if(!sleeping || age % 2 == 0){
            food -= 1;
        }
        if(!sleeping){
            thirst -= 1;
        }
        if(food < 0){ food = 0; }
        if(thirst < 0){ thirst = 0; }
        if(food <= 0){
            takeDamage(3, "fame");
        }else if(thirst <= 0){
            takeDamage(3, "sete");
        }else if(food >= 85 && thirst >= 85 && healt < maxHealthInt()){
            healt++;
        }

        // Finestra di valutazione: tengo la mutazione del cervello solo se ha fatto uguale o meglio
        windowTick++;
        if (windowTick >= WINDOW_SIZE) {
            double score = (Netreward - windowStartReward) / windowTick;
            if (score >= bestWindowScore) {
                bestBrain = Brain.copy();
                bestWindowScore = score;
            } else {
                Brain = bestBrain.copy();
            }
            Brain.mutate(MUTATION_RATE);
            windowStartReward = Netreward;
            windowTick = 0;
        }

        if(healt <= 0){
            Simulation.kill(this, lastDamageCause);
        }
    }

    private int chooseAction(double[] output){
        if(rng().nextDouble() < EXPLORATION){
            return rng().nextInt(OUTPUT_SIZE);
        }
        int best = 0;
        for (int i = 1; i < output.length; i++) {
            if (output[i] > output[best]) {
                best = i;
            }
        }
        return best;
    }

    private void perform(int action){
        actionCounts[action]++;
        switch (action) {
            case ACT_IDLE:
                Netreward -= 0.5;
                break;
            case ACT_MOVE_XP: move(1, 0); break;
            case ACT_MOVE_XM: move(-1, 0); break;
            case ACT_MOVE_ZP: move(0, 1); break;
            case ACT_MOVE_ZM: move(0, -1); break;
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
                reproduce();
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

    private void move(int dx, int dz){
        int nx = position[0].get() + dx;
        int nz = position[2].get() + dz;
        int dim = world.getDim();
        if(nx < 0 || nx >= dim || nz < 0 || nz >= dim){
            Netreward -= 10;
            return;
        }
        if(world.isWaterColumn(nx, nz)){
            // L'acqua non si attraversa a nuoto: ci si puo' solo affacciare per bere
            Netreward -= 2;
            return;
        }
        if(!spend(MOVE_COST)){ return; }
        // Ricompensa di avvicinamento: se ha fame o sete, avvicinarsi alla risorsa e' un passo giusto
        double before = (food < 150 || thirst < 150) ? nearestNeed() : -1;
        position[0].set(nx);
        position[1].set(world.surfaceY(nx, nz)); // segue l'altezza del terreno (salita/discesa di 1 cella)
        position[2].set(nz);
        if(before >= 0){
            double after = nearestNeed();
            if(after >= 0){ Netreward += 1.0 * (before - after); }
        }
        Netreward += 0.5;
    }

    /** La piu' vicina tra cibo visibile (se ha fame) e acqua visibile (se ha sete). */
    private double nearestNeed(){
        double food = this.food < 150 ? Vision.nearestFood(this) : -1;
        double water = this.thirst < 150 ? Vision.nearestWater(this) : -1;
        if(food < 0){ return water; }
        if(water < 0){ return food; }
        return Math.min(food, water);
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
        if(thirst != old){ Netreward += 3.0; }
    }

    // ------------------------------------------------------------------ mangiare

    private void eat(){
        int x = position[0].get();
        int y = position[1].get();
        int z = position[2].get();
        boolean apple = "M".equals(world.getSymbol(x, y, z));
        Corpse corpse = Simulation.corpseAt(x, y, z);

        // I carnivori preferiscono il cadavere, gli altri la mela
        if(corpse != null && (!apple || stats.diet == DietType.CARNIVORE)){
            eatCorpse(corpse);
        }else if(apple){
            eatApple(x, y, z);
        }else{
            Netreward -= 2.0;
        }
    }

    private double appleEfficiency(){
        switch (stats.diet) {
            case HERBIVORE: return 1.0;
            case OMNIVORE: return 0.8;
            default: return 0.25;
        }
    }

    private double meatEfficiency(){
        switch (stats.diet) {
            case HERBIVORE: return 0.25;
            case OMNIVORE: return 0.8;
            default: return 1.2;
        }
    }

    private void eatApple(int x, int y, int z){
        int oldFood = food;
        world.remove(x, y, z, "M");
        food += (int) Math.round(100 * stats.foodAbsorption * appleEfficiency());
        if(food > maxFood){ food = maxFood; }
        consumedFood++;
        if(oldFood != food){
            Netreward += 3.0;
            applesEaten++; // conta solo le mele che hanno davvero dato cibo
        }
    }

    private void eatCorpse(Corpse corpse){
        int oldFood = food;
        double bite = corpse.takeBite(BITE_MEAT);
        food += (int) Math.round(bite * stats.foodAbsorption * meatEfficiency());
        if(food > maxFood){ food = maxFood; }
        consumedFood++;
        // Il cibo ottenuto dal corpo e' cio' che si premia (non l'attacco in se')
        if(oldFood != food){ Netreward += 4.0 * (bite / BITE_MEAT); }
    }

    // ------------------------------------------------------------------ combattimento

    void takeDamage(int amount, String cause){
        healt -= amount;
        lastDamageCause = cause;
        Netreward -= amount * 0.2; // si punisce la salute persa
    }

    private void attack(){
        int x = position[0].get();
        int y = position[1].get();
        int z = position[2].get();
        entity target = null;
        for(entity o : Entity_manager.snapshotAlive()){
            if(o == this || !o.isAlive()){ continue; }
            int[] p = o.getPos();
            if(p[1] != y || Math.abs(p[0] - x) > 1 || Math.abs(p[2] - z) > 1){ continue; }
            if(target == null || o.healt < target.healt){
                target = o;
            }
        }
        if(target == null){
            Netreward -= 1.0; // colpo a vuoto
            return;
        }
        int damage = Math.max(1, (int) Math.round(stats.attackPower * (0.8 + 0.4 * rng().nextDouble())));
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
            Netreward -= 0.5; // dormire da riposato non serve
        }else{
            Netreward += need;
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
        for(entity o : Entity_manager.snapshotAlive()){
            if(o == this || o.sex == this.sex || !o.canMate()){ continue; }
            int[] p = o.getPos();
            if(p[1] == y && Math.abs(p[0] - x) <= PARTNER_RANGE && Math.abs(p[2] - z) <= PARTNER_RANGE){
                candidates.add(o);
            }
        }
        if(candidates.isEmpty()){ return null; }
        return candidates.get(rng().nextInt(candidates.size()));
    }

    /** Due genitori: l'entita' che sceglie di riprodursi cerca un partner vicino di sesso opposto. */
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
        child.food = 120;

        this.food -= REPRO_FOOD_COST;
        partner.food -= REPRO_FOOD_COST;
        this.reproCooldown = REPRO_COOLDOWN;
        partner.reproCooldown = REPRO_COOLDOWN;
        this.Netreward += 20;
        partner.Netreward += 10;

        Simulation.queueBirth(child);
        Genealogy.recordBirth(child, this, partner);
        System.out.println("Nascita ID "+id+" da "+EntityID+" e "+partner.getId()
                +" (generazione "+childGenome.lineage().generation()+")");
    }

    // ------------------------------------------------------------------ genoma

    private int maxHealthInt(){
        return (int) Math.round(stats.maxHealth);
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
