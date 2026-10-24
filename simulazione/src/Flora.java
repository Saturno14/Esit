package src;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Alberi, mele e semi: il cibo vegetale del mondo.
 *
 * PERCHE' NON PIU' LO SPAWN CASUALE. Prima le mele comparivano in una colonna a caso ogni due
 * tick, fino a un tetto fisso. Con un cibo cosi' il mondo non ha geografia: ogni punto vale
 * quanto ogni altro punto, quindi non c'e' niente da ricordare, niente da cercare, niente per cui
 * valga la pena essere piu' alti o piu' veloci di un altro - e infatti la popolazione si
 * appiattiva contro il tetto e la selezione si spegneva. Con gli alberi il cibo sta in POSTI, e
 * un posto si puo' raggiungere, difendere, perdere: e' da qui che vengono le pressioni.
 *
 * COME FUNZIONA. Alla generazione del mondo nascono alcune ZONE boscose (vedi
 * {@link #generate}). Ogni albero ha parametri propri - velocita' di crescita, altezza massima,
 * raggio della chioma, produzione - quindi cresce per conto suo e vale piu' o meno di un altro.
 * Un albero maturo fa crescere mele sulla chioma; la mela cresce, a meta' della crescita CADE
 * (lontano dal tronco in proporzione al raggio della chioma), a terra continua a maturare, al
 * massimo della maturazione ha una piccola probabilita' di lasciare un SEME, e se non viene
 * mangiata marcisce.
 *
 * DUE MODI DI MANGIARE. Una mela a terra la raccoglie chiunque le arrivi accanto. Una mela ancora
 * sulla chioma la raccoglie solo chi ARRIVA IN ALTEZZA - corpo piu' braccia, vedi
 * {@link #pickFrom}. E' l'unico posto del progetto in cui essere alti paga da solo, e serve
 * esattamente a questo: dare un senso selettivo alla taglia, che fin qui era solo un costo.
 *
 * IL VALORE NUTRITIVO DIPENDE DALLA MATURAZIONE ({@link Apple#nutrition()}): una mela verde
 * sfama poco, una matura sfama il doppio, una che sta per marcire di nuovo poco. Quindi anche
 * QUANDO mangiare e' una scelta, non solo cosa.
 */
public final class Flora {

    /** Con false il mondo torna senza alberi (per i confronti A/B con lo spawn casuale). */
    public static volatile boolean enabled = true;

    // ------------------------------------------------------------------ parametri degli alberi

    /** Crescita in altezza per tick di un albero a velocita' 1: da seme ad adulto in ~1200 tick. */
    private static final double HEIGHT_PER_TICK = 0.004;
    /** Altezza di partenza di un albero appena germogliato (m). */
    private static final double SEEDLING_HEIGHT = 0.35;
    /** Frazione dell'altezza massima oltre la quale l'albero comincia a fare mele. */
    private static final double MATURITY = 0.55;
    /** Un tick su quanti un albero maturo prova a mettere una mela, a produzione 1. */
    private static final int FRUIT_PERIOD = 120;

    // ------------------------------------------------------------------ parametri delle mele

    /** Crescita della mela per tick a velocita' d'albero 1: ciclo completo in ~540 tick. */
    private static final double APPLE_GROWTH_PER_TICK = 0.0025;
    /** Maturazione a cui la mela si stacca dal ramo. */
    private static final double FALL_AT = 0.50;
    /** Maturazione piena: massimo valore nutritivo, ed e' qui che si decide il seme. */
    private static final double RIPE_AT = 1.00;
    /** Maturazione a cui la mela marcisce e scompare. */
    private static final double ROT_AT = 1.35;
    /** Probabilita' che una mela matura a terra lasci un seme. */
    private static final double SEED_CHANCE = 0.04;

    // ------------------------------------------------------------------ parametri dei semi

    /** Tick di attesa prima che un seme possa germogliare. */
    private static final int SEED_DELAY = 200;
    /** Probabilita' che un seme, passata l'attesa, diventi un albero. */
    private static final double GERM_CHANCE = 0.25;
    /**
     * Distanza minima (celle) tra due tronchi: un albero non germoglia addosso a un altro.
     *
     * A 2 l'esclusione era un quadrato di 5x5 per albero, cioe' al massimo un albero ogni 25
     * celle: una zona boscosa non arrivava a dieci alberi e il mondo intero ne aveva venti, troppo
     * pochi per nutrire le fondatrici. A 1 un bosco e' un bosco.
     */
    private static final int MIN_SPACING = 1;

    private static final Object LOCK = new Object();
    private static final List<Tree> trees = new ArrayList<>();
    private static final List<Apple> apples = new ArrayList<>();
    private static final List<Seed> seeds = new ArrayList<>();
    /** Mele per colonna (x*dim+z): la vista e la raccolta chiedono "cosa c'e' in questa cella". */
    private static final Map<Integer, List<Apple>> byColumn = new HashMap<>();
    /** Tronco per colonna: serve a bloccare il passo e a far vedere l'albero. */
    private static final Map<Integer, Tree> trunkByColumn = new HashMap<>();
    private static Random rng = new Random(0);
    private static int nextTreeId = 1;
    private static int nextAppleId = 1;

    private Flora() {
    }

    // ================================================================== l'albero

    public static final class Tree {
        public final int id;
        public final int x;
        public final int z;
        /** Quota (celle = metri) del piede del tronco. */
        public final int baseY;
        /** Moltiplicatore di velocita': vale su altezza E maturazione delle sue mele. */
        public final double growthSpeed;
        /** Altezza da adulto (m). */
        public final double maxHeight;
        /** Raggio della chioma (m): decide dove cadono le mele e quanto e' larga da vedere. */
        public final double crownRadius;
        /** Quante mele fa: entra nel ritmo di produzione e nel numero di mele che la chioma tiene. */
        public final double production;
        private double height;
        private final long bornTick;

        private Tree(int id, int x, int z, int baseY, double growthSpeed, double maxHeight,
                     double crownRadius, double production, double height, long bornTick) {
            this.id = id;
            this.x = x;
            this.z = z;
            this.baseY = baseY;
            this.growthSpeed = growthSpeed;
            this.maxHeight = maxHeight;
            this.crownRadius = crownRadius;
            this.production = production;
            this.height = height;
            this.bornTick = bornTick;
        }

        public double height() {
            return height;
        }

        /**
         * Raggio del tronco (m): cresce con l'altezza dell'albero.
         *
         * STA QUI, e non nel disegno, perche' adesso il tronco e' anche un OSTACOLO FISICO: se il
         * raggio con cui si disegna e quello con cui si urta fossero due numeri diversi, le
         * entita' sbatterebbero contro l'aria o passerebbero attraverso il legno. La formula e'
         * quella che il motore grafico usava da sempre (Renderer).
         */
        public double trunkRadius() {
            return 0.07 + 0.045 * height;
        }

        public boolean mature() {
            return height >= maxHeight * MATURITY;
        }

        /** Quanto e' cresciuto, da 0 (seme) a 1 (adulto): per il disegno e i log. */
        public double maturity() {
            return Math.min(1.0, height / maxHeight);
        }

        /** Raggio della chioma ADESSO: cresce con l'albero, non e' subito quello da adulto. */
        public double crownNow() {
            return crownRadius * (0.35 + 0.65 * maturity());
        }

        /** Quante mele la chioma puo' tenere insieme. */
        private int crownCapacity() {
            return Math.max(1, (int) Math.round(production * 4));
        }
    }

    // ================================================================== la mela

    public static final class Apple {
        public final int id;
        public final int treeId;
        /** Posizione continua in metri (x, z) e quota in metri (y). */
        public double x;
        public double z;
        public double y;
        /** Cella di appartenenza: aggiornata quando la mela cade. */
        public int cx;
        public int cz;
        public int cy;
        /** Vero finche' sta appesa alla chioma. */
        public boolean onTree;
        /** Maturazione: 0 appena nata, FALL_AT quando cade, RIPE_AT al massimo, ROT_AT marcisce. */
        public double growth;
        /** Velocita' di maturazione, ereditata dall'albero che l'ha fatta. */
        public final double speed;
        private boolean seedDone;

        private Apple(int id, int treeId, double x, double y, double z, boolean onTree, double speed) {
            this.id = id;
            this.treeId = treeId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.onTree = onTree;
            this.speed = speed;
            this.growth = 0;
        }

        /**
         * Valore nutritivo come moltiplicatore del cibo di una mela, in base alla maturazione.
         *
         * Verde sfama poco, matura sfama al massimo, passata torna a sfamare poco. La curva e'
         * spezzata in due rette e non e' una campana liscia perche' quello che conta e' che
         * l'ottimo sia UN momento riconoscibile: se il valore cambiasse poco attorno al massimo,
         * aspettare il momento giusto non sarebbe una strategia distinguibile dal mangiare subito.
         */
        public double nutrition() {
            if (growth <= FALL_AT) {
                // verde: dal 25% al 45% salendo
                return 0.25 + 0.20 * (growth / FALL_AT);
            }
            if (growth <= RIPE_AT) {
                return 0.45 + 0.75 * ((growth - FALL_AT) / (RIPE_AT - FALL_AT));
            }
            double over = (growth - RIPE_AT) / (ROT_AT - RIPE_AT);
            return 1.20 - 0.85 * Math.min(1.0, over);
        }

        /**
         * Quanto e' grossa, da 0.45 (appena allegata) a 1 (matura): per il disegno. Una mela non
         * nasce della dimensione finale, e vederla crescere e' il modo piu' diretto di capire a
         * occhio che l'albero sta lavorando.
         */
        public double sizeFactor() {
            return 0.45 + 0.55 * Math.min(1.0, growth / RIPE_AT);
        }

        /**
         * Maturazione normalizzata da 0 a 1 sull'intera vita della mela (verde -> matura ->
         * marcia): il colore la segue, cosi' lo stato di una mela si legge da lontano senza
         * aprire un log.
         */
        public double ripeness() {
            return Math.min(1.0, growth / ROT_AT);
        }

        /** Nome della fase, per i log e per leggere cosa sta succedendo. */
        public String stage() {
            if (onTree) {
                return growth < FALL_AT * 0.6 ? "verde" : "quasi matura";
            }
            if (growth < RIPE_AT) {
                return "matura";
            }
            return growth < ROT_AT * 0.95 ? "passata" : "marcia";
        }
    }

    // ================================================================== il seme

    private static final class Seed {
        final int x;
        final int z;
        final long plantedTick;
        /** I parametri dell'albero che l'ha fatto: un figlio somiglia al padre, con variazione. */
        final double growthSpeed;
        final double maxHeight;
        final double crownRadius;
        final double production;

        Seed(int x, int z, long tick, double growthSpeed, double maxHeight,
             double crownRadius, double production) {
            this.x = x;
            this.z = z;
            this.plantedTick = tick;
            this.growthSpeed = growthSpeed;
            this.maxHeight = maxHeight;
            this.crownRadius = crownRadius;
            this.production = production;
        }
    }

    // ================================================================== generazione del mondo

    /**
     * Mette i boschi di partenza: alcune ZONE sparse, dentro ognuna un gruppo di alberi con
     * densita' che cala verso il bordo.
     *
     * Gli alberi di partenza nascono GIA' CRESCIUTI (non da seme) perche' se al tick 0 non ci
     * fosse cibo gia' maturo la prima generazione morirebbe prima che il primo albero fruttifichi,
     * e non misureremmo l'evoluzione ma la fame.
     */
    public static void generate(int dim, long seed) {
        synchronized (LOCK) {
            trees.clear();
            apples.clear();
            seeds.clear();
            byColumn.clear();
            trunkByColumn.clear();
            nextTreeId = 1;
            nextAppleId = 1;
            rng = new Random(seed * 31 + 7);
            if (!enabled) {
                System.out.println("Flora: disattivata (mele a spawn casuale)");
                return;
            }

            int zones = Math.max(4, (int) Math.round(dim * dim / 320.0));
            int planted = 0;
            for (int zone = 0; zone < zones; zone++) {
                int[] centre = world.randomLandColumn(rng);
                double zoneRadius = 4 + rng.nextDouble() * 5;       // 4..9 celle
                int attempts = 26 + rng.nextInt(18);
                for (int a = 0; a < attempts; a++) {
                    // raggio con radice: dentro al cerchio la densita' resta uniforme, poi la
                    // sfoltiamo verso il bordo con una probabilita' che cala
                    double r = zoneRadius * Math.sqrt(rng.nextDouble());
                    if (rng.nextDouble() > 1.0 - 0.6 * (r / zoneRadius)) {
                        continue;
                    }
                    double ang = rng.nextDouble() * 2 * Math.PI;
                    int tx = centre[0] + (int) Math.round(Math.cos(ang) * r);
                    int tz = centre[1] + (int) Math.round(Math.sin(ang) * r);
                    if (plant(tx, tz, dim, randomParams(), 0, true) != null) {
                        planted++;
                    }
                }
            }
            // mele gia' in giro: ogni albero adulto parte con la chioma a mezzo carico
            int fruited = 0;
            for (Tree t : trees) {
                if (!t.mature()) {
                    continue;
                }
                int n = Math.max(1, t.crownCapacity() / 2);
                for (int i = 0; i < n; i++) {
                    Apple ap = growApple(t, dim);
                    if (ap != null) {
                        ap.growth = rng.nextDouble() * FALL_AT * 0.9;
                        fruited++;
                    }
                }
            }
            System.out.println("Flora: " + planted + " alberi in " + zones + " zone, "
                    + fruited + " mele sulle chiome");
        }
    }

    private static double[] randomParams() {
        return new double[]{
                0.5 + rng.nextDouble(),                 // velocita' 0.5..1.5
                2.5 + rng.nextDouble() * 3.5,           // altezza massima 2.5..6.0 m
                1.2 + rng.nextDouble() * 2.3,           // chioma 1.2..3.5 m
                0.3 + rng.nextDouble() * 1.2            // produzione 0.3..1.5
        };
    }

    /** Parametri del figlio: quelli del padre con una variazione del 15%. */
    private static double[] mutateParams(double[] p) {
        return new double[]{
                clamp(p[0] * jitter(), 0.4, 1.8),
                clamp(p[1] * jitter(), 2.0, 7.0),
                clamp(p[2] * jitter(), 1.0, 4.0),
                clamp(p[3] * jitter(), 0.2, 1.8)
        };
    }

    private static double jitter() {
        return 1.0 + (rng.nextDouble() - 0.5) * 0.30;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /**
     * Prova a piantare un albero. Ritorna null se il posto non va bene: fuori mappa, in acqua,
     * o troppo vicino a un altro tronco.
     */
    private static Tree plant(int x, int z, int dim, double[] p, long tick, boolean grown) {
        if (x < 0 || x >= dim || z < 0 || z >= dim) {
            return null;
        }
        if (world.isWaterColumn(x, z)) {
            return null;
        }
        for (int dx = -MIN_SPACING; dx <= MIN_SPACING; dx++) {
            for (int dz = -MIN_SPACING; dz <= MIN_SPACING; dz++) {
                if (trunkByColumn.containsKey(key(x + dx, z + dz, dim))) {
                    return null;
                }
            }
        }
        double height = grown ? p[1] * (0.6 + rng.nextDouble() * 0.4) : SEEDLING_HEIGHT;
        Tree t = new Tree(nextTreeId++, x, z, world.surfaceY(x, z),
                p[0], p[1], p[2], p[3], height, tick);
        trees.add(t);
        trunkByColumn.put(key(x, z, dim), t);
        return t;
    }

    // ================================================================== il passo del tempo

    /** Un tick di vita vegetale: alberi che crescono, mele che maturano e cadono, semi. */
    public static void step(long tick) {
        if (!enabled) {
            return;
        }
        int dim = world.getDim();
        synchronized (LOCK) {
            for (Tree t : trees) {
                if (t.height < t.maxHeight) {
                    t.height = Math.min(t.maxHeight, t.height + HEIGHT_PER_TICK * t.growthSpeed);
                }
                if (t.mature() && countOnTree(t) < t.crownCapacity()) {
                    // la produzione decide il RITMO: piu' produttivo = tenta piu' spesso
                    double chance = t.production / FRUIT_PERIOD;
                    if (rng.nextDouble() < chance) {
                        growApple(t, dim);
                    }
                }
            }
            List<Apple> gone = null;
            for (Apple a : apples) {
                a.growth += APPLE_GROWTH_PER_TICK * a.speed;
                if (a.onTree && a.growth >= FALL_AT) {
                    drop(a, dim);
                }
                if (!a.onTree && !a.seedDone && a.growth >= RIPE_AT) {
                    a.seedDone = true;
                    if (rng.nextDouble() < SEED_CHANCE) {
                        Tree parent = treeById(a.treeId);
                        double[] p = parent != null
                                ? mutateParams(new double[]{parent.growthSpeed, parent.maxHeight,
                                        parent.crownRadius, parent.production})
                                : randomParams();
                        seeds.add(new Seed(a.cx, a.cz, tick, p[0], p[1], p[2], p[3]));
                    }
                }
                if (a.growth >= ROT_AT) {
                    if (gone == null) {
                        gone = new ArrayList<>();
                    }
                    gone.add(a);
                }
            }
            if (gone != null) {
                for (Apple a : gone) {
                    forget(a, dim);
                }
            }
            if (!seeds.isEmpty()) {
                seeds.removeIf(s -> {
                    if (tick - s.plantedTick < SEED_DELAY) {
                        return false;
                    }
                    if (rng.nextDouble() < GERM_CHANCE) {
                        plant(s.x, s.z, dim, new double[]{s.growthSpeed, s.maxHeight,
                                s.crownRadius, s.production}, tick, false);
                    }
                    return true;     // germogliato o perso, il seme ha finito
                });
            }
        }
    }

    private static int countOnTree(Tree t) {
        int n = 0;
        for (Apple a : apples) {
            if (a.onTree && a.treeId == t.id) {
                n++;
            }
        }
        return n;
    }

    /** Centro della chioma in metri: sta appena sotto la cima del tronco. */
    static double crownCentreY(Tree t) {
        return t.baseY + t.height - CROWN_DROP * t.crownNow();
    }

    /** Quanto il centro della chioma sta sotto la cima, in frazioni del raggio della chioma. */
    static final double CROWN_DROP = 0.42;
    /** Dove stanno le mele rispetto al raggio della chioma: sul guscio esterno, non dentro. */
    private static final double FRUIT_SHELL_MIN = 0.82;

    /**
     * Appende una mela nuova sul GUSCIO della chioma.
     *
     * Non dentro al volume: una mela dentro alla chioma non si vedrebbe (le foglie sono opache) e
     * sarebbe un frutto che esiste solo nei log. Sul guscio e' dove stanno i frutti veri - in
     * punta ai rami - ed e' anche cio' che rende sensato il fatto che cadano lontano dal tronco in
     * proporzione al raggio della chioma.
     */
    private static Apple growApple(Tree t, int dim) {
        double crown = t.crownNow();
        double ang = rng.nextDouble() * 2 * Math.PI;
        // direzione sul guscio: la componente verticale sta nella meta' bassa, i frutti pendono
        double vertical = -0.65 + rng.nextDouble() * 0.85;
        double horizontal = Math.sqrt(Math.max(0, 1 - vertical * vertical));
        double shell = crown * (FRUIT_SHELL_MIN + rng.nextDouble() * 0.18);
        double ax = t.x + 0.5 + Math.cos(ang) * horizontal * shell;
        double az = t.z + 0.5 + Math.sin(ang) * horizontal * shell;
        double ay = crownCentreY(t) + vertical * shell;
        int cx = (int) Math.floor(ax);
        int cz = (int) Math.floor(az);
        if (cx < 0 || cx >= dim || cz < 0 || cz >= dim) {
            cx = t.x;
            cz = t.z;
            ax = t.x + 0.5;
            az = t.z + 0.5;
        }
        Apple a = new Apple(nextAppleId++, t.id, ax, ay, az, true, t.growthSpeed);
        a.cx = cx;
        a.cz = cz;
        a.cy = (int) Math.floor(ay);
        apples.add(a);
        index(a, dim);
        return a;
    }

    /**
     * La mela si stacca e finisce a terra. La distanza dal tronco la decide il raggio della
     * chioma: una mela cresciuta in punta a un ramo lungo cade lontano.
     */
    private static void drop(Apple a, int dim) {
        Tree t = treeById(a.treeId);
        unindex(a, dim);
        a.onTree = false;
        if (t != null) {
            double ang = rng.nextDouble() * 2 * Math.PI;
            double r = t.crownNow() * Math.sqrt(rng.nextDouble());
            double nx = t.x + 0.5 + Math.cos(ang) * r;
            double nz = t.z + 0.5 + Math.sin(ang) * r;
            int cx = (int) Math.floor(nx);
            int cz = (int) Math.floor(nz);
            // fuori mappa o in acqua: la mela resta al piede dell'albero invece di sparire
            if (cx < 0 || cx >= dim || cz < 0 || cz >= dim || world.isWaterColumn(cx, cz)) {
                nx = t.x + 0.5;
                nz = t.z + 0.5;
                cx = t.x;
                cz = t.z;
            }
            a.x = nx;
            a.z = nz;
            a.cx = cx;
            a.cz = cz;
        }
        a.cy = world.surfaceY(a.cx, a.cz);
        a.y = a.cy;
        index(a, dim);
    }

    // ================================================================== comandi (console/GUI)

    /**
     * Pianta un albero adulto in quella colonna. Ritorna null se il posto non va bene (fuori
     * mappa, in acqua, troppo vicino a un altro tronco): il chiamante lo dice all'utente invece
     * di far finta che sia andata.
     */
    public static Tree plantTree(int x, int z, long tick) {
        int dim = world.getDim();
        synchronized (LOCK) {
            return plant(x, z, dim, randomParams(), tick, true);
        }
    }

    /**
     * Mette una mela a terra in quella colonna, alla maturazione data (0 verde, 1 matura).
     * Serve ai comandi e alle prove: una mela nata senza albero non ha un treeId valido, quindi
     * non lascera' semi con i parametri di nessuno - prendera' parametri casuali, che per una
     * mela messa a mano e' il comportamento giusto.
     */
    public static Apple plantApple(int x, int z, double growth) {
        int dim = world.getDim();
        if (x < 0 || x >= dim || z < 0 || z >= dim || world.isWaterColumn(x, z)) {
            return null;
        }
        synchronized (LOCK) {
            Apple a = new Apple(nextAppleId++, -1, x + 0.5, world.surfaceY(x, z), z + 0.5,
                    false, 1.0);
            a.growth = Math.max(0, Math.min(ROT_AT * 0.95, growth));
            a.cx = x;
            a.cz = z;
            a.cy = world.surfaceY(x, z);
            apples.add(a);
            index(a, dim);
            return a;
        }
    }

    /** Riepilogo per la console: quanti alberi, mele, semi e a che punto sono. */
    public static String report() {
        synchronized (LOCK) {
            if (!enabled) {
                return "Flora disattivata (mele a spawn casuale)";
            }
            int appese = 0;
            double maturazione = 0;
            for (Apple a : apples) {
                if (a.onTree) {
                    appese++;
                }
                maturazione += a.growth;
            }
            double alt = 0;
            double chioma = 0;
            int maturi = 0;
            for (Tree t : trees) {
                alt += t.height;
                chioma += t.crownNow();
                if (t.mature()) {
                    maturi++;
                }
            }
            int nt = Math.max(1, trees.size());
            return String.format(java.util.Locale.ROOT,
                    "Alberi: %d (%d maturi), altezza media %.2f m, chioma media %.2f m%n"
                    + "Mele: %d (%d sugli alberi, %d a terra), maturazione media %.2f%n"
                    + "Semi in attesa: %d",
                    trees.size(), maturi, alt / nt, chioma / nt,
                    apples.size(), appese, apples.size() - appese,
                    apples.isEmpty() ? 0 : maturazione / apples.size(),
                    seeds.size());
        }
    }

    // ================================================================== accesso dal resto del mondo

    /**
     * True se in questa colonna c'e' una mela che un'entita' con i piedi a py puo' VEDERE.
     * Vedere non vuol dire raggiungere: una mela in cima a un albero si vede da lontano e non si
     * prende, ed e' proprio quella differenza che rende utile essere alti.
     */
    public static boolean visibleAppleAt(int x, int z, int py) {
        if (!enabled) {
            return false;
        }
        int dim = world.getDim();
        synchronized (LOCK) {
            List<Apple> here = byColumn.get(key(x, z, dim));
            if (here == null || here.isEmpty()) {
                return false;
            }
            return entity.reachableLevel(py, world.surfaceY(x, z));
        }
    }

    /** True se in questa colonna c'e' il tronco di un albero (si vede, e non si attraversa). */
    public static boolean isTrunk(int x, int z) {
        if (!enabled) {
            return false;
        }
        synchronized (LOCK) {
            return trunkByColumn.containsKey(key(x, z, world.getDim()));
        }
    }

    /**
     * La mela migliore che un'entita' puo' prendere in questa colonna, o null.
     *
     * footY e' la quota dei piedi in metri e pickHeight quanto arriva in alto da li' (corpo piu'
     * braccia alzate). Una mela a terra la prende chiunque; una sulla chioma solo chi ci arriva.
     * Tra piu' mele raggiungibili si sceglie la piu' NUTRIENTE, non la piu' vicina: scegliere
     * quale mela valga la pena, dentro una cella, non e' una decisione che l'entita' debba
     * imparare - le basta sapere che mangiare conviene.
     */
    public static Apple pickFrom(int x, int z, int footY, double pickHeight) {
        if (!enabled) {
            return null;
        }
        int dim = world.getDim();
        synchronized (LOCK) {
            List<Apple> here = byColumn.get(key(x, z, dim));
            if (here == null || here.isEmpty()) {
                return null;
            }
            Apple best = null;
            for (Apple a : here) {
                if (!a.onTree) {
                    if (!entity.reachableLevel(footY, a.cy)) {
                        continue;
                    }
                } else if (a.y - footY > pickHeight) {
                    continue;       // troppo in alto per questo corpo
                }
                if (best == null || a.nutrition() > best.nutrition()) {
                    best = a;
                }
            }
            return best;
        }
    }

    /** Toglie la mela dal mondo (mangiata). Ritorna il suo valore nutritivo. */
    public static double take(Apple a) {
        int dim = world.getDim();
        synchronized (LOCK) {
            double value = a.nutrition();
            forget(a, dim);
            return value;
        }
    }

    public static int countApples() {
        synchronized (LOCK) {
            return apples.size();
        }
    }

    public static int countTrees() {
        synchronized (LOCK) {
            return trees.size();
        }
    }

    public static int countSeeds() {
        synchronized (LOCK) {
            return seeds.size();
        }
    }

    /** Quante mele sono ancora appese (diagnostica: se sono quasi tutte, nessuno arriva in alto). */
    public static int countOnTree() {
        synchronized (LOCK) {
            int n = 0;
            for (Apple a : apples) {
                if (a.onTree) {
                    n++;
                }
            }
            return n;
        }
    }

    /** Copia delle mele, per il motore grafico (che gira su un altro thread). */
    public static List<Apple> snapshotApples() {
        synchronized (LOCK) {
            return new ArrayList<>(apples);
        }
    }

    /** Copia degli alberi, per il motore grafico. */
    public static List<Tree> snapshotTrees() {
        synchronized (LOCK) {
            return new ArrayList<>(trees);
        }
    }

    // ================================================================== salvataggio

    public static org.json.JSONObject toJson() {
        synchronized (LOCK) {
            org.json.JSONObject o = new org.json.JSONObject();
            org.json.JSONArray ts = new org.json.JSONArray();
            for (Tree t : trees) {
                org.json.JSONObject j = new org.json.JSONObject();
                j.put("id", t.id);
                j.put("x", t.x);
                j.put("z", t.z);
                j.put("baseY", t.baseY);
                j.put("growthSpeed", t.growthSpeed);
                j.put("maxHeight", t.maxHeight);
                j.put("crownRadius", t.crownRadius);
                j.put("production", t.production);
                j.put("height", t.height);
                j.put("bornTick", t.bornTick);
                ts.put(j);
            }
            o.put("trees", ts);
            org.json.JSONArray as = new org.json.JSONArray();
            for (Apple a : apples) {
                org.json.JSONObject j = new org.json.JSONObject();
                j.put("id", a.id);
                j.put("treeId", a.treeId);
                j.put("x", a.x);
                j.put("y", a.y);
                j.put("z", a.z);
                j.put("onTree", a.onTree);
                j.put("growth", a.growth);
                j.put("speed", a.speed);
                j.put("seedDone", a.seedDone);
                as.put(j);
            }
            o.put("apples", as);
            org.json.JSONArray ss = new org.json.JSONArray();
            for (Seed s : seeds) {
                org.json.JSONObject j = new org.json.JSONObject();
                j.put("x", s.x);
                j.put("z", s.z);
                j.put("plantedTick", s.plantedTick);
                j.put("growthSpeed", s.growthSpeed);
                j.put("maxHeight", s.maxHeight);
                j.put("crownRadius", s.crownRadius);
                j.put("production", s.production);
                ss.put(j);
            }
            o.put("seeds", ss);
            o.put("nextTreeId", nextTreeId);
            o.put("nextAppleId", nextAppleId);
            return o;
        }
    }

    public static void fromJson(org.json.JSONObject o) {
        int dim = world.getDim();
        synchronized (LOCK) {
            trees.clear();
            apples.clear();
            seeds.clear();
            byColumn.clear();
            trunkByColumn.clear();
            org.json.JSONArray ts = o.optJSONArray("trees");
            if (ts != null) {
                for (int i = 0; i < ts.length(); i++) {
                    org.json.JSONObject j = ts.getJSONObject(i);
                    Tree t = new Tree(j.getInt("id"), j.getInt("x"), j.getInt("z"),
                            j.getInt("baseY"), j.getDouble("growthSpeed"), j.getDouble("maxHeight"),
                            j.getDouble("crownRadius"), j.getDouble("production"),
                            j.getDouble("height"), j.optLong("bornTick", 0));
                    trees.add(t);
                    trunkByColumn.put(key(t.x, t.z, dim), t);
                }
            }
            org.json.JSONArray as = o.optJSONArray("apples");
            if (as != null) {
                for (int i = 0; i < as.length(); i++) {
                    org.json.JSONObject j = as.getJSONObject(i);
                    Apple a = new Apple(j.getInt("id"), j.getInt("treeId"), j.getDouble("x"),
                            j.getDouble("y"), j.getDouble("z"), j.getBoolean("onTree"),
                            j.optDouble("speed", 1.0));
                    a.growth = j.getDouble("growth");
                    a.seedDone = j.optBoolean("seedDone", false);
                    a.cx = (int) Math.floor(a.x);
                    a.cz = (int) Math.floor(a.z);
                    a.cy = (int) Math.floor(a.y);
                    apples.add(a);
                    index(a, dim);
                }
            }
            org.json.JSONArray ss = o.optJSONArray("seeds");
            if (ss != null) {
                for (int i = 0; i < ss.length(); i++) {
                    org.json.JSONObject j = ss.getJSONObject(i);
                    seeds.add(new Seed(j.getInt("x"), j.getInt("z"), j.getLong("plantedTick"),
                            j.getDouble("growthSpeed"), j.getDouble("maxHeight"),
                            j.getDouble("crownRadius"), j.getDouble("production")));
                }
            }
            nextTreeId = o.optInt("nextTreeId", trees.size() + 1);
            nextAppleId = o.optInt("nextAppleId", apples.size() + 1);
        }
    }

    // ================================================================== indice per colonna

    private static int key(int x, int z, int dim) {
        return x * dim + z;
    }

    private static void index(Apple a, int dim) {
        byColumn.computeIfAbsent(key(a.cx, a.cz, dim), k -> new ArrayList<>()).add(a);
    }

    private static void unindex(Apple a, int dim) {
        int k = key(a.cx, a.cz, dim);
        List<Apple> here = byColumn.get(k);
        if (here != null) {
            here.remove(a);
            if (here.isEmpty()) {
                byColumn.remove(k);
            }
        }
    }

    private static void forget(Apple a, int dim) {
        unindex(a, dim);
        apples.remove(a);
    }

    private static Tree treeById(int id) {
        for (Tree t : trees) {
            if (t.id == id) {
                return t;
            }
        }
        return null;
    }
}
