package src;

import java.util.List;

/**
 * Vista EGOCENTRICA a cono (rete a input FISSO).
 *
 * L'entita' non vede piu' tutto cio' che la circonda: vede dentro un cono centrato su dove sta
 * guardando (corpo + testa, vedi entity.getGazeDirection), largo quanto dice il gene
 * stat.field_of_view. Il cono e' diviso in {@link #SECTORS} settori, e per ogni categoria di
 * oggetto importante (mela, acqua, cadavere, altra entita') ogni settore riporta un solo numero:
 * la vicinanza dell'oggetto piu' vicino entro il raggio visivo (gene sightDistance, ridotto
 * dal buio: vedi entity.sightNow), da 1 (adiacente) verso 0 (al limite). Zero vuol dire "li' non
 * vedo niente", che adesso
 * comprende anche "li' non sto guardando".
 *
 * IL SETTORE 0 E' A SINISTRA DEL CONO E L'ULTIMO A DESTRA, quindi il significato di un settore e'
 * sempre lo stesso rispetto al MUSO, non rispetto al nord. E' questo che rende la vista
 * egocentrica: "cibo davanti" si impara una volta, non quattro volte come quando i settori erano
 * i punti cardinali e la stessa scena produceva ingressi diversi a seconda di come l'entita' era
 * girata.
 *
 * I SETTORI SI SPARTISCONO IL CONO, non il giro completo: un occhio stretto ha quindi una
 * risoluzione angolare fine e un occhio largo una grossolana. E' un compromesso vero, deciso dal
 * genoma, non un parametro scelto a tavolino.
 *
 * I quattro valori "qui" (cosa c'e' nella propria cella) restano fuori dal cono: quello e' tatto,
 * non vista, e si sente anche a occhi chiusi.
 *
 * Il raggio e l'ampiezza cambiano da entita' a entita' ma la dimensione dell'uscita e' sempre la
 * stessa, quindi tutti i cervelli hanno la stessa topologia (eredita', crossover e salvataggio
 * restano semplici).
 */
public final class Vision {

    public static final int SECTORS = 8;

    /**
     * CATEGORIE DI OGGETTI: una sola cifra per settore, la vicinanza di quello piu' vicino.
     * Le altre entita' NON sono qui: hanno un blocco di attributi tutto loro (vedi ENT_*).
     */
    public static final int OBJ_CATEGORIES = 4;
    public static final int CAT_APPLE = 0;
    public static final int CAT_WATER = 1;
    public static final int CAT_CORPSE = 2;
    /**
     * L'ALBERO. E' una categoria a parte dalla mela perche' le due cose dicono il contrario:
     * una mela e' cibo adesso e sparisce quando la mangi, un albero e' un POSTO dove il cibo
     * ricompare. Senza un ingresso per l'albero, un'entita' che ha appena ripulito una chioma
     * vede il posto esattamente come vede un deserto, e non ha modo di tornarci. E' questo
     * ingresso che rende il bosco una geografia invece di un caso fortunato.
     */
    public static final int CAT_TREE = 3;

    /**
     * GLI ATTRIBUTI DELLE ALTRE ENTITA': quello che SI VEDE di chi si ha davanti, mai chi e'.
     *
     * Prima le altre entita' erano una categoria come le mele: un numero per settore, la
     * vicinanza. Dentro quel numero finivano maschi, femmine, cuccioli, aggressori, rivali e
     * compagne, indistinguibili. La tentazione era aggiungere un canale "partner possibile"
     * (sesso opposto, maturo, sazio): sarebbe stato passare alla rete l'IDENTITA' dell'altro,
     * cioe' dirle la risposta invece di darle gli indizi.
     *
     * Qui invece ogni settore riporta l'entita' piu' vicina con cio' che di lei si vede: quanto
     * e' vicina, quanto e' grossa, quanto sta in forze, se sta guardando verso di me, se si sta
     * muovendo, e di che sesso e' (il dimorfismo si vede: vedi ENT_SEX). Niente dieta, niente
     * parentela, e soprattutto nessun "quello li' e' un partner valido": maturita', sazieta' e
     * attesa non si vedono, quindi non si passano. Se un'entita' deve distinguere una compagna da
     * un rivale deve impararlo da questi indizi - e, un giorno, da cosa l'altro dice.
     *
     * I DUE CANALI DI SEGNALE NASCONO MUTI (sempre 0) e sono riservati al dialogo: quando le
     * entita' potranno emettere un segnale saranno due USCITE in piu' che riempiono due ingressi
     * che esistono gia', non una riscrittura della percezione. Il posto si prepara adesso perche'
     * ogni cambio del tracciato degli ingressi invalida i cervelli salvati, e vale la pena
     * pagarlo una volta sola.
     */
    public static final int ENT_ATTRS = 8;
    /** Vicinanza, da 1 (addosso) verso 0 (al limite della vista). 0 = nessuno in quel settore. */
    public static final int ENT_NEAR = 0;
    /** Mole: l'altezza apparente del corpo, normalizzata. Si vede. */
    public static final int ENT_SIZE = 1;
    /** Vigore: salute su salute massima. Un ferito si vede. */
    public static final int ENT_VIGOR = 2;
    /** Quanto il suo muso punta verso di me, da 0 (di spalle) a 1 (mi guarda in faccia). */
    public static final int ENT_LOOKS = 3;
    /** 1 se si sta muovendo. */
    public static final int ENT_MOVES = 4;
    /**
     * IL SESSO: 1 femmina, 0.5 maschio (deciso, non derivato - vedi sotto).
     *
     * E' l'unico attributo "di identita'" che si passa, e si passa per scelta: nel mondo vero il
     * dimorfismo sessuale SI VEDE, quindi non e' barare come lo sarebbe un canale "questo e' un
     * partner valido" (sesso opposto, maturo, sazio, senza attesa), che darebbe alla rete la
     * risposta invece degli indizi. Qui si dice solo com'e' fatto l'altro: che quello sia o no un
     * partner possibile resta da capire, perche' maturita', sazieta' e attesa non si passano.
     *
     * PERCHE' 0.5 E NON 0 PER IL MASCHIO: in questo blocco lo zero ha gia' un significato, ed e'
     * "in questo settore non c'e' nessuno" (tutti gli attributi restano a zero). Con il maschio a
     * 0 un maschio davanti e il vuoto davanti darebbero lo stesso ingresso su questo canale.
     */
    public static final int ENT_SEX = 5;
    /** Primo canale di segnale: per ora sempre 0. */
    public static final int ENT_SIGNAL_A = 6;
    /** Secondo canale di segnale: per ora sempre 0. */
    public static final int ENT_SIGNAL_B = 7;

    /** Inizio del blocco degli oggetti: indice = IDX_OBJ + categoria * SECTORS + settore. */
    public static final int IDX_OBJ = 0;
    /** Inizio del blocco delle entita': indice = IDX_ENT + attributo * SECTORS + settore. */
    public static final int IDX_ENT = OBJ_CATEGORIES * SECTORS;
    /**
     * I valori "QUI": cosa c'e' nella PROPRIA cella (le quattro categorie di oggetti piu' la
     * presenza di un'altra entita'). Restano fuori dal cono perche' quello e' tatto, non vista,
     * e si sente anche a occhi chiusi - ma si sente solo SOTTO I PIEDI, non nelle celle intorno.
     */
    public static final int IDX_HERE = IDX_ENT + ENT_ATTRS * SECTORS;
    public static final int HERE_VALUES = OBJ_CATEGORIES + 1;
    public static final int HERE_ENTITY = OBJ_CATEGORIES;
    /**
     * Indice dell'ingresso "non vedo niente": 1 quando il cono e' completamente vuoto.
     *
     * Serve perche' con la vista a cono "non vedere" e' uno STATO in cui si puo' restare per
     * sempre: senza un ingresso che lo dica, l'unico modo che la rete ha di accorgersene e' la
     * somma di tanti zeri, e da tanti zeri non si distingue "non c'e' niente davanti" da "ho gli
     * occhi chiusi". E' l'ingresso che permette l'istinto piu' importante della vista a cono: se
     * non vedi niente, gira la testa.
     */
    public static final int IDX_NOTHING = IDX_HERE + HERE_VALUES;

    public static final int SIZE = IDX_NOTHING + 1;

    private Vision() {
    }

    /**
     * Mela nella colonna (x,z) visibile da chi ha i piedi a py: con gli alberi attivi la chiede a
     * Flora, che sa anche delle mele appese in aria. Vedi ActionWeights.levelTolerance: si guarda
     * entro un dislivello, non alla propria quota esatta.
     *
     * Attenzione: VISIBILE non vuol dire raggiungibile. Una mela in cima a un albero si vede e
     * non si prende (vedi Flora.pickFrom), ed e' giusto che sia cosi': l'entita' deve poter
     * desiderare qualcosa che il suo corpo non raggiunge, altrimenti l'altezza non viene mai
     * selezionata.
     */
    private static boolean appleAt(int x, int z, int py) {
        if (Flora.enabled) {
            return Flora.visibleAppleAt(x, z, py);
        }
        int y = world.surfaceY(x, z);
        return entity.reachableLevel(py, y) && "M".equals(world.getSymbol(x, y, z));
    }

    public static double[] sense(entity self) {
        double radius = self.sightNow();
        double gaze = self.getGazeDirection();
        double half = Math.toRadians(self.getStats().fieldOfView) / 2.0;
        int[] pos = self.getPos();
        int px = pos[0];
        int py = pos[1];
        int pz = pos[2];
        int dim = world.getDim();

        double[][] sector = new double[OBJ_CATEGORIES][SECTORS];
        double[] here = new double[HERE_VALUES];
        // per ogni settore l'entita' piu' vicina, e di quella si guarda l'aspetto
        entity[] vista = new entity[SECTORS];
        double[] vistaD = new double[SECTORS];
        for (int i = 0; i < SECTORS; i++) {
            vistaD[i] = Double.MAX_VALUE;
        }

        // Mele, acqua e alberi: celle del mondo entro il raggio
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            int x = px + dx;
            if (x < 0 || x >= dim) continue;
            for (int dz = -r; dz <= r; dz++) {
                int z = pz + dz;
                if (z < 0 || z >= dim) continue;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius) continue;
                if (appleAt(x, z, py)) {
                    register(sector, here, CAT_APPLE, dx, dz, d, radius, gaze, half);
                }
                if (world.isWaterColumn(x, z)) {
                    register(sector, here, CAT_WATER, dx, dz, d, radius, gaze, half);
                }
                // L'albero si vede a QUALUNQUE quota entro il raggio: e' alto diversi metri, e
                // un gradino non lo nasconde come nasconde una mela per terra.
                if (Flora.isTrunk(x, z)) {
                    register(sector, here, CAT_TREE, dx, dz, d, radius, gaze, half);
                }
            }
        }

        // Cadaveri
        for (Corpse c : Simulation.corpsesSnapshot()) {
            if (c.isConsumed() || !entity.reachableLevel(py, c.getY())) continue;
            int dx = c.getX() - px;
            int dz = c.getZ() - pz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= radius) {
                register(sector, here, CAT_CORPSE, dx, dz, d, radius, gaze, half);
            }
        }

        // Altre entita' vive: si tiene la PIU' VICINA per settore. Due entita' nello stesso
        // settore non si sommano come si somma la frutta: davanti c'e' qualcuno, ed e' uno.
        for (entity o : Entity_manager.snapshotAlive()) {
            if (o == self || !o.isAlive()) continue;
            int[] op = o.getPos();
            if (!entity.reachableLevel(py, op[1])) continue;
            int dx = op[0] - px;
            int dz = op[2] - pz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > radius) continue;
            if (dx == 0 && dz == 0) {
                here[HERE_ENTITY] = 1.0;
                continue;
            }
            int sct = sectorOf(dx, dz, gaze, half);
            if (sct < 0) continue;              // fuori dal cono: non la vede
            if (d < vistaD[sct]) {
                vistaD[sct] = d;
                vista[sct] = o;
            }
        }

        double[] out = new double[SIZE];
        boolean sawSomething = false;
        for (int cat = 0; cat < OBJ_CATEGORIES; cat++) {
            for (int sct = 0; sct < SECTORS; sct++) {
                double v = sector[cat][sct];
                out[IDX_OBJ + cat * SECTORS + sct] = v;
                sawSomething |= v > 0;
            }
        }
        for (int sct = 0; sct < SECTORS; sct++) {
            entity o = vista[sct];
            if (o == null) continue;            // tutti i suoi attributi restano a zero
            sawSomething = true;
            double d = vistaD[sct];
            out[IDX_ENT + ENT_NEAR * SECTORS + sct] =
                    Math.max(0.0, Math.min(1.0, (radius - d + 1.0) / radius));
            out[IDX_ENT + ENT_SIZE * SECTORS + sct] = mole(o);
            out[IDX_ENT + ENT_VIGOR * SECTORS + sct] = vigore(o);
            out[IDX_ENT + ENT_LOOKS * SECTORS + sct] = miGuarda(o, px, pz);
            out[IDX_ENT + ENT_MOVES * SECTORS + sct] = o.isWalking() ? 1.0 : 0.0;
            out[IDX_ENT + ENT_SEX * SECTORS + sct] = o.getSex() == 2 ? 1.0 : 0.5;
            out[IDX_ENT + ENT_SIGNAL_A * SECTORS + sct] = o.getSignalA();
            out[IDX_ENT + ENT_SIGNAL_B * SECTORS + sct] = o.getSignalB();
        }
        for (int i = 0; i < HERE_VALUES; i++) {
            out[IDX_HERE + i] = here[i];
        }
        out[IDX_NOTHING] = sawSomething ? 0.0 : 1.0;
        return out;
    }

    /**
     * Mole apparente: la MASSA del corpo rapportata al doppio della massa di riferimento.
     *
     * Non l'altezza: misurata su 68 corpi, l'altezza va da 1.90 a 2.97 metri con mediana 2.44,
     * cioe' sono tutti alti uguale e l'ingresso saturava a 1 per chiunque - un attributo che vale
     * sempre lo stesso non e' un'informazione. La massa invece va da 19 a 315 chili con mediana
     * 72: li' una differenza si vede. Satura solo sopra i 230 chili, cioe' per i giganti veri.
     */
    private static double mole(entity o) {
        double m = o.getTraits().mass() / (2 * src.body.BodyTraits.REFERENCE_MASS);
        return Math.max(0.0, Math.min(1.0, m));
    }

    /** Vigore apparente: salute su salute massima. */
    private static double vigore(entity o) {
        return Math.max(0.0, Math.min(1.0, o.getHealt() / o.getStats().maxHealth));
    }

    /**
     * Quanto l'altro sta guardando verso di me: 1 se mi ha in faccia, 0 se e' girato altrove.
     * E' il coseno dell'angolo tra il suo sguardo e la direzione che porta da lui a me, azzerato
     * quando e' negativo (di spalle e' di spalle, non e' "meno che di spalle").
     */
    private static double miGuarda(entity o, int px, int pz) {
        int[] op = o.getPos();
        double dx = px - op[0];
        double dz = pz - op[2];
        if (dx == 0 && dz == 0) {
            return 1.0;
        }
        double verso = Math.atan2(dx, dz);
        return Math.max(0.0, Math.cos(wrap(verso - o.getGazeDirection())));
    }

    /**
     * Settore del cono in cui cade un oggetto spostato di (dx, dz), oppure -1 se e' fuori dal
     * cono. L'angolo si misura con la stessa convenzione di entity.facing: atan2(x, z), cioe' Z
     * in avanti e X di lato.
     */
    static int sectorOf(int dx, int dz, double gaze, double half) {
        double angle = Math.atan2(dx, dz);
        double rel = wrap(angle - gaze);
        if (Math.abs(rel) > half) {
            return -1;
        }
        int s = (int) Math.floor((rel + half) / (2 * half) * SECTORS);
        return Math.max(0, Math.min(SECTORS - 1, s));
    }

    /**
     * Quanto un oggetto e' FUORI ASSE rispetto a dove si sta guardando, in radianti, oppure -1
     * se cade fuori dal cono. Serve a mirare: a zero ce l'ho esattamente nel muso.
     */
    static double offAxis(double dx, double dz, double gaze, double half) {
        if (dx == 0 && dz == 0) {
            return 0;
        }
        double rel = wrap(Math.atan2(dx, dz) - gaze);
        return Math.abs(rel) > half ? -1 : Math.abs(rel);
    }

    /** Riporta un angolo in [-pi, pi) (meno pi greco e pi greco sono lo stesso angolo). */
    static double wrap(double a) {
        double x = (a + Math.PI) % (2 * Math.PI);
        if (x < 0) {
            x += 2 * Math.PI;
        }
        return x - Math.PI;
    }

    /**
     * NESSUN SENSO A 360 GRADI. In sense() c'era anche l'acqua delle OTTO CELLE INTORNO, che
     * scriveva il valore "qui acqua" anche per una pozza di lato: era l'unica cosa in tutta la
     * percezione che non passava dal cono, e con la vista a cono non ha piu' senso. Bere resta
     * possibile da una cella adiacente (vedi entity.drink: quella e' la realta' fisica, il
     * braccio ci arriva), ma l'entita' non SA di poterlo fare finche' non ci gira la testa. E'
     * proprio il lavoro che l'istinto "non vedo niente, guarda in giro" deve imparare a fare.
     */
    private static void register(double[][] sector, double[] here, int cat,
                                 int dx, int dz, double d, double radius, double gaze, double half) {
        if (dx == 0 && dz == 0) {
            here[cat] = 1.0;
            return;
        }
        int s = sectorOf(dx, dz, gaze, half);
        if (s < 0) {
            return;     // fuori dal cono: non lo vede
        }
        double closeness = Math.max(0.0, Math.min(1.0, (radius - d + 1.0) / radius));
        if (closeness > sector[cat][s]) {
            sector[cat][s] = closeness;
        }
    }

    /*
     * QUI C'ERANO nearestFood e nearestWater, che servivano solo alla ricompensa di avvicinamento
     * (vedi entity.walk): tolta quella, non le usa piu' nessuno. Erano anche l'ultimo posto in
     * cui la dieta faceva un filtro secco ("mela se non carnivoro"), e con l'abitudine
     * alimentare al posto delle tre scatole quel filtro non ha piu' senso.
     */

    /** Dentro il cono (la propria cella si "vede" sempre: e' sotto i piedi). */
    private static boolean visible(int dx, int dz, double gaze, double half) {
        return (dx == 0 && dz == 0) || sectorOf(dx, dz, gaze, half) >= 0;
    }
}
