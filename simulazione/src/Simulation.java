package src;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ciclo di simulazione CENTRALE, a TEMPO CONTINUO.
 *
 * Un solo thread fa avanzare il mondo a FETTE brevi (FETTA_S, un sessantesimo di secondo di mondo):
 * a ogni fetta la fisica integra, ogni corpo pubblica dove e' arrivato, e decidono SOLO le entita'
 * il cui orologio e' scaduto. L'orologio e' DEL CORPO, non del mondo: ogni entita' decide con il
 * periodo che il suo gene speed le da' (entity.periodoMotorio), in momenti scollegati da quelli
 * delle altre. Il "tick" da mezzo secondo resta, ma solo per quello che e' davvero a tempo di
 * mondo: metabolismo, flora, campionamento, giorno e notte.
 *
 * LA SIMULTANEITA' NON E' UN THREAD PER ENTITA'. Era cosi' nella prima versione del progetto, e
 * l'utente la vuole di nuovo nella sostanza: entita' indipendenti, che agiscono insieme, e che se
 * vogliono la stessa mela nello stesso istante la contendono. Ma con i thread chi vince la mela lo
 * decide lo scheduler del sistema operativo - cioe' il caso, non il corpo - e si perde il seme
 * riproducibile su cui si regge tutto il metodo di misura (confronti appaiati). Quindi la
 * simultaneita' e' fatta cosi', dentro ogni fetta:
 *  1. tutte le entita' in scadenza GUARDANO LO STESSO MONDO e scelgono (entity.decidi): nessuna
 *     tocca niente, quindi l'ordine non conta per costruzione;
 *  2. le scelte diventano fatti IN ORDINE DI VELOCITA' MOTORIA (entity.esegui): chi e' piu' veloce
 *     arriva prima alla mela, e a parita' decide il caso, dichiarato;
 *  3. le morti della fetta si dichiarano alla fine, tutte insieme: due colpi nello stesso
 *     istante arrivano ENTRAMBI, anche se uno dei due uccide.
 * La fetta e' la finestra di simultaneita'. Senza di essa, con tempi reali, "nello stesso istante"
 * avrebbe probabilita' zero e nessuna contesa esisterebbe mai.
 *
 * Lo shuffle delle entita' ("ordine casuale: nessuna entita' e' sempre prima") non c'e' piu':
 * esisteva per nascondere l'iniquita' di un ordine sequenziale, e adesso l'ordine e' una
 * proprieta' dei corpi.
 */
public class Simulation {

    /** Durata di un tick a timeScale 1: il passo del metabolismo e di tutto cio' che e' a tempo di mondo. */
    public static final long TICK_MS = 500;
    public static final double TICK_S = TICK_MS / 1000.0;
    /**
     * LA FETTA: quanto tempo di mondo passa fra due letture del mondo, ed e' la finestra di
     * simultaneita'. Un sessantesimo di secondo: sotto la soglia di percezione di chi guarda, e
     * due passi di fisica (PhysicsWorld.TIME_STEP e' 1/120). Piu' corta: contese piu' rare e piu'
     * costo per secondo di mondo; piu' lunga: il movimento torna a scatti.
     */
    public static final double FETTA_S = 1.0 / 60.0;
    /** Tick per "giorno" (incrementa world.cycle). */
    public static final int TICKS_PER_DAY = 240;
    /** Per quanti tick un cadavere resta nel mondo se nessuno lo finisce. */
    public static final int CORPSE_TICKS = 240;

    private static final int APPLE_INTERVAL_TICKS = 2;
    /**
     * Densita' bersaglio per cella (mele e popolazione massima): quella che funzionava bene
     * sulla mappa 20x20 originale (60 su 400 celle = 0.15/cella). Con mappe piu' grandi il
     * numero massimo di mele/entita' cresce di conseguenza, altrimenti risorse e spazio vitale
     * diventano via via piu' scarsi solo perche' la mappa e' piu' grande.
     */
    private static final double DENSITY_PER_CELL = 60.0 / (20 * 20);
    private static final int SUMMARY_INTERVAL_TICKS = 20;

    /**
     * Lock tenuto durante ogni tick: chi deve leggere o modificare il mondo in modo
     * consistente (Save, Load) lo prende per essere sicuro che non ci sia un tick a meta'.
     */
    public static final Object LOCK = new Object();

    private static volatile boolean running = false;
    private static volatile int generation = 0;
    private static Thread thread;
    private static volatile double timeScale = 1.0;
    private static volatile long tickCount = 0;
    /** Secondi di mondo trascorsi: e' l'orologio contro cui ogni corpo misura il proprio. */
    private static volatile double tempoMondo = 0;
    /** A che secondo di mondo scatta il prossimo tick (metabolismo e il resto). */
    private static double prossimoTick = TICK_S;
    /** Quante fette sono passate: serve al sorteggio delle parita' (vedi fetta). */
    private static long fette = 0;

    private static long seed = System.nanoTime();
    private static Random rng = new Random(seed);

    private static final List<Corpse> corpses = new ArrayList<>();
    private static final List<entity> pendingBirths = new ArrayList<>();

    private static int births = 0;
    private static int deathsStarved = 0;
    /**
     * MORTE DI SETE: era contata tra gli OMICIDI, ed e' stato un bug per molte sessioni.
     * Qui c'era "if (cause.startsWith(\"fame\")) deathsStarved++; else deathsKilled++;", e la
     * causa che un'entita' morta di sete porta e' "sete", che non comincia per "fame": finiva
     * quindi nel conteggio dei combattimenti, insieme a "ucciso da #N".
     * Quanto pesava, contato dalla genealogia della sessione 19: fame 127, SETE 126,
     * combattimento 52. Il log diceva "178 uccise" e le uccisioni vere erano 52, cioe' il 17%
     * delle morti e non il 57%. La sete uccide quanto la fame, e nessuno lo sapeva perche' era
     * mascherata dagli omicidi (e perche' avg_thirst non era nel log).
     * History.morte le tre cause le ha sempre distinte giuste: era solo questo conteggio.
     */
    private static int deathsThirst = 0;
    private static int deathsKilled = 0;
    /** Morti divise per sesso (1 maschio, 2 femmina): vedi kill(). */
    private static int deathsMale = 0;
    private static int deathsFemale = 0;

    private Simulation() {
    }

    // ------------------------------------------------------------------ controllo

    /** Imposta il seed di tutta la simulazione (decisioni, geni, mele, cervelli). */
    public static synchronized void setSeed(long newSeed) {
        seed = newSeed;
        rng = new Random(newSeed);
        brain.seed(newSeed + 1);
    }

    public static long getSeed() {
        return seed;
    }

    /** Generatore condiviso: si usa solo dal thread di simulazione (o prima che parta). */
    public static Random random() {
        return rng;
    }

    public static synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        final int myGeneration = ++generation;
        thread = new Thread(() -> loop(myGeneration), "esit-simulation");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        /*
         * CHI E' ANCORA VIVO E' PROPRIO QUELLO CHE INTERESSA. Un mondo che finisce con qualcuno
         * in piedi finisce con i migliori camminatori della sessione ancora vivi: registrando
         * solo i morti, l'archivio raccoglierebbe per definizione chi non ce l'ha fatta.
         */
        if (running) {
            for (entity e : Entity_manager.snapshotAlive()) {
                e.registraMovimento();
            }
        }
        src.body.GaitArchive.salva();
        src.body.MondoFisico.chiudi();
        running = false;
        generation++; // invalida un eventuale thread ancora in uscita
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    public static boolean isRunning() {
        return running;
    }

    /** Azzera lo stato della simulazione (nuovo NewStart o Load). */
    public static void reset() {
        synchronized (corpses) {
            corpses.clear();
        }
        synchronized (pendingBirths) {
            pendingBirths.clear();
        }
        tickCount = 0;
        tempoMondo = 0;
        prossimoTick = TICK_S;
        fette = 0;
        births = 0;
        deathsStarved = 0;
        deathsThirst = 0;
        deathsKilled = 0;
        deathsMale = 0;
        deathsFemale = 0;
    }

    /**
     * Velocita' massima della simulazione. Il tetto lascia a ogni tick abbastanza tempo per la fisica dei
     * corpi (TICK_MS / 50 = 10 ms per tick); con la fisica attiva il limite ha senso tenerlo basso.
     */
    public static final double MAX_TIME_SCALE = 50.0;

    public static void setTimeScale(double scale) {
        timeScale = Math.max(0.1, Math.min(MAX_TIME_SCALE, scale));
    }

    public static double getTimeScale() {
        return timeScale;
    }

    public static long getTick() {
        return tickCount;
    }

    /** Secondi di mondo trascorsi dall'inizio della sessione. */
    public static double tempoMondo() {
        return tempoMondo;
    }

    /** Usato dal Load per riprendere dal tick salvato. */
    public static void setTick(long tick) {
        tickCount = tick;
        tempoMondo = tick * TICK_S;
        prossimoTick = tempoMondo + TICK_S;
    }

    // ------------------------------------------------------------------ eventi

    /** Un'entita' nata in questo tick entra nel mondo a fine tick. */
    public static void queueBirth(entity child) {
        synchronized (pendingBirths) {
            pendingBirths.add(child);
        }
    }

    /** Uccide un'entita': esce dal ciclo e lascia un cadavere nel punto in cui si trova. */
    public static void kill(entity e, String cause) {
        if (!e.life.compareAndSet(true, false)) {
            return;
        }
        e.markDead(cause, tickCount);
        Entity_manager.Entity_remuve(e);
        // quello che questo corpo aveva imparato a fare con le sue gambe resta all'archivio: e'
        // il solo momento in cui si sa quanta strada ha fatto davvero (src.body.GaitArchive)
        e.registraMovimento();
        // e scende dal palco, se ci stava: una ragdoll senza entita' e' solo calcolo
        src.body.MondoFisico.esce(e.getId());
        // nessuno verra' piu' a ritirare le prove di allenamento di questa entita'
        src.body.Locomotion.forget(e.getId());

        int[] p = e.getPos();
        double meat = e.getStats().maxHealth * 1.5;
        synchronized (corpses) {
            corpses.add(new Corpse(e, p[0], p[1], p[2], meat, tickCount + CORPSE_TICKS));
        }
        History.morte(cause);
        if (cause.startsWith("fame")) {
            deathsStarved++;
        } else if (cause.startsWith("sete")) {
            deathsThirst++;
        } else {
            deathsKilled++;
        }
        // morti divise per sesso: serve a distinguere "un sesso muore di piu'" da "la moneta
        // del sesso alla nascita e' andata storta", che portano alla stessa popolazione di soli
        // maschi ma si correggono in modi opposti
        if (e.getSex() == 2) {
            deathsFemale++;
        } else {
            deathsMale++;
        }
        Genealogy.recordDeath(e, cause, tickCount);
        System.out.println("Morte ID " + e.getId() + " (" + cause + ") a eta' " + e.getAge());
    }

    /** Cadavere ancora commestibile nella cella indicata, oppure null. */
    public static Corpse corpseAt(int x, int y, int z) {
        synchronized (corpses) {
            for (Corpse c : corpses) {
                if (!c.isConsumed() && c.getX() == x && c.getY() == y && c.getZ() == z) {
                    return c;
                }
            }
        }
        return null;
    }

    /** Copia dei cadaveri presenti (usata da visione e motore grafico). */
    public static List<Corpse> corpsesSnapshot() {
        synchronized (corpses) {
            return new ArrayList<>(corpses);
        }
    }

    // ------------------------------------------------------------------ loop

    private static void loop(int myGeneration) {
        while (running && myGeneration == generation) {
            long startNs = System.nanoTime();
            try {
                if (!world.isPaused()) {
                    synchronized (LOCK) {
                        fetta();
                    }
                }
            } catch (Throwable t) {
                System.out.println("Errore nella fetta di simulazione: " + t);
                t.printStackTrace();
            }
            /*
             * Il tempo vero che una fetta deve durare. Se la fisica ne prende di piu' (molti
             * corpi, velocita' alta) il mondo va piu' lento di quanto chiesto: e' il prezzo
             * dichiarato del mondo unico (voce 26), e si paga rallentando, non saltando fette.
             */
            long budgetNs = (long) (FETTA_S / timeScale * 1e9);
            long restoNs = budgetNs - (System.nanoTime() - startNs);
            if (restoNs > 0) {
                java.util.concurrent.locks.LockSupport.parkNanos(restoNs);
            }
            if (Thread.interrupted() && (!running || myGeneration != generation)) {
                break;
            }
        }
    }

    /**
     * Esegue n tick a mano, anche se la simulazione e' in pausa.
     *
     * Serve a guardare una cosa che succede in un tick solo: in pausa non succede niente, a
     * velocita' normale e' gia' passata. Prende lo stesso lucchetto del ciclo, quindi non puo'
     * sovrapporsi a un tick automatico; se la simulazione non e' in pausa i tick si aggiungono a
     * quelli suoi, ed e' il motivo per cui il comando lo dice.
     *
     * @return quanti tick sono stati eseguiti
     */
    public static int passo(int quanti) {
        int fatti = 0;
        for (int i = 0; i < quanti; i++) {
            synchronized (LOCK) {
                long prima = tickCount;
                while (tickCount == prima) {
                    fetta();
                }
            }
            fatti++;
        }
        return fatti;
    }

    /**
     * UNA FETTA DI MONDO: la fisica avanza, i corpi si leggono, chi e' in scadenza decide e poi
     * agisce, i morti si contano. Quando il tempo passa un multiplo di TICK_S scatta anche il
     * tick (metabolismo, flora, campionamento). Vedi l'intestazione della classe per il perche'
     * di quest'ordine.
     */
    private static void fetta() {
        tempoMondo += FETTA_S;
        fette++;

        List<entity> alive = Entity_manager.snapshotAlive();
        allestisciMondo(alive);
        src.body.MondoFisico.avanza(FETTA_S);
        for (entity e : alive) {
            e.leggiDalCorpo();
        }

        // chi e' in scadenza secondo il PROPRIO orologio
        List<entity> inScadenza = new ArrayList<>();
        for (entity e : alive) {
            if (e.isAlive() && e.inScadenza(tempoMondo)) {
                inScadenza.add(e);
            }
        }
        if (!inScadenza.isEmpty()) {
            // FASE 1: tutte guardano lo stesso mondo e scelgono. Nessuna tocca niente.
            for (entity e : inScadenza) {
                e.decidi(tempoMondo);
            }
            /*
             * FASE 2: le scelte diventano fatti, e l'ordine e' una proprieta' dei corpi: chi ha la
             * velocita' motoria piu' alta arriva prima. A PARITA' DECIDE IL CASO, ma un caso che
             * non consuma il generatore condiviso (altrimenti ogni parita' sposterebbe i geni dei
             * figli che nascono dopo): una chiave mescolata da id e numero della fetta.
             */
            final long fettaCorrente = fette;
            inScadenza.sort((a, b) -> {
                int c = Double.compare(b.velocitaMotoria(), a.velocitaMotoria());
                return c != 0 ? c : Long.compare(chiaveParita(a.getId(), fettaCorrente),
                        chiaveParita(b.getId(), fettaCorrente));
            });
            for (entity e : inScadenza) {
                e.esegui();
            }
        }
        /*
         * LE MORTI DELLA FETTA, TUTTE INSIEME. Un colpo non uccide piu' nell'istante in cui arriva
         * (entity.attack): chi muore in questa fetta ha ancora fatto la sua azione, quindi due
         * entita' che si colpiscono nello stesso istante si colpiscono davvero entrambe.
         */
        for (entity e : alive) {
            if (e.isAlive() && e.getHealt() <= 0) {
                kill(e, e.causaDanno());
            }
        }
        flushBirths();

        if (tempoMondo + 1e-9 >= prossimoTick) {
            prossimoTick += TICK_S;
            tick();
        }
    }

    /** Un numero pseudo-casuale da (id, fetta): decide le parita' senza toccare il generatore. */
    private static long chiaveParita(long id, long fetta) {
        long z = (id * 0x9E3779B97F4A7C15L) ^ (fetta * 0xBF58476D1CE4E5B9L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * IL TICK: quello che e' a tempo di mondo e non a tempo di corpo. Mezzo secondo. Niente qui
     * decide o muove: il movimento e le decisioni stanno in fetta().
     */
    private static void tick() {
        tickCount++;

        List<entity> alive = Entity_manager.snapshotAlive();
        for (entity e : alive) {
            if (e.isAlive()) {
                e.tickMetabolico();
            }
        }

        flushBirths();
        synchronized (corpses) {
            corpses.removeIf(c -> c.isConsumed() || tickCount >= c.getExpireTick());
        }
        if (Flora.enabled) {
            Flora.step(tickCount);
        } else {
            spawnApples();
        }

        History.campiona(tickCount);
        if (tickCount % TICKS_PER_DAY == 0) {
            world.advanceCycle();
        }
        if (tickCount % SUMMARY_INTERVAL_TICKS == 0) {
            StatsLog.record(tickCount, world.countApples(), corpsesSnapshot().size(),
                    births, deathsStarved, deathsThirst, deathsKilled,
                    deathsMale, deathsFemale);
            System.out.println("[tick " + tickCount + "] entita'=" + Entity_manager.Entity_count()
                    + " " + DayNight.phaseName()
                    + " mele=" + world.countApples()
                    + (Flora.enabled ? "(" + Flora.countOnTree() + " sugli alberi)"
                        + " alberi=" + Flora.countTrees() : "")
                    + " cadaveri=" + corpsesSnapshot().size()
                    + " nascite=" + births
                    + " morti(fame/sete/lotta)=" + deathsStarved + "/" + deathsThirst
                        + "/" + deathsKilled
                    + " azioni=" + java.util.Arrays.toString(entity.actionCounts));
        }
        if (tickCount % 100 == 0) {
            Genealogy.save();
            // non riscrive niente se nessuno ha battuto un primato: vedi GaitArchive.salva
            src.body.GaitArchive.salva();
        }
    }

    /**
     * Mette nel mondo fisico TUTTE le entita' vive, senza budget e senza capienza.
     *
     * Qui c'era la scelta di chi "saliva sul palco": un budget di tempo per tick decideva quante
     * ragdoll stavano in fisica, e le altre camminavano con una formula. Era necessario finche' la
     * fisica era un distributore di metri; in un mondo unico e' una contraddizione - due entita'
     * nella stessa radura, una che inciampa nelle buche e l'altra che le attraversa, non vivono
     * nello stesso mondo e non si possono confrontare. Quindi o ci sono tutte o la fisica e'
     * spenta per tutte (ActionWeights.fisicaViva).
     *
     * IL PREZZO E' DICHIARATO: a velocita' alte il tick dura meno del tempo che la fisica chiede,
     * e la simulazione va piu' lenta della velocita' richiesta. Rallentare e' la cosa onesta;
     * togliere corpi dalla fisica no.
     *
     * La posizione serve solo alla prima messa in campo (vedi MondoFisico.Candidato): a ogni fetta
     * la si passa di nuovo perche' le entita' nuove arrivano in ogni momento, ma per chi e' gia'
     * nel mondo viene ignorata.
     */
    private static void allestisciMondo(List<entity> alive) {
        if (ActionWeights.fisicaViva <= 0) {
            src.body.MondoFisico.allestisci(java.util.List.of());
            return;
        }
        List<src.body.MondoFisico.Candidato> candidati = new ArrayList<>(alive.size());
        for (entity e : alive) {
            candidati.add(new src.body.MondoFisico.Candidato(e.getId(), e.getAdultBody(),
                    e.getGait(), e.statoDelCorpo(), e.sterzoDelCorpo(),
                    e.getExactX(), e.getExactZ(), e.getFacing()));
        }
        src.body.MondoFisico.allestisci(candidati);
    }

    private static void flushBirths() {
        List<entity> born;
        synchronized (pendingBirths) {
            born = new ArrayList<>(pendingBirths);
            pendingBirths.clear();
        }
        for (entity child : born) {
            Entity_manager.Entity_add(child);
            births++;
            History.nascita();
        }
    }

    /** Numero massimo di mele in giro, scalato sull'area della mappa (vedi DENSITY_PER_CELL). */
    /** Morti per sesso: [maschi, femmine]. */
    /** Morti per causa: fame, sete, combattimento. */
    public static int[] mortiPerCausa() {
        return new int[]{deathsStarved, deathsThirst, deathsKilled};
    }

    public static int[] mortiPerSesso() {
        return new int[]{deathsMale, deathsFemale};
    }

    public static int maxApples() {
        int dim = world.getDim();
        return Math.max(60, (int) Math.round(dim * dim * DENSITY_PER_CELL));
    }

    /**
     * IL LIMITE DI SICUREZZA, che non e' un tetto di popolazione.
     *
     * Qui c'era maxPopulation: la stessa densita' delle mele, 375 entita' su una mappa 50x50 -
     * cioe' ESATTAMENTE il numero massimo di mele. Un tetto a quel valore impediva alla
     * popolazione di arrivare dove il cibo non basta piu', e quindi impediva la cosa che si
     * voleva vedere: la competizione. La fame non limitava niente, perche' c'era un limite prima
     * della fame. Via (decisione dell'utente del 25/10/2026: "togliendo anche il cap di entita'
     * massime costringeremo queste ultime a competere per il cibo").
     *
     * Quello che resta e' un limite GEOMETRICO e di macchina, non ecologico: un'entita' per cella
     * di terreno. Non e' una scelta sull'ecologia perche' a quella densita' i corpi non ci
     * starebbero fisicamente in piedi, e la fisica costa circa 1 ms per corpo per tick, quindi
     * molto prima di arrivarci la simulazione sarebbe diventata inguardabile. Se questo numero
     * viene raggiunto non e' il limite che ha funzionato: e' il mondo che non si e' limitato da
     * se', ed e' un risultato da guardare, non da nascondere.
     *
     * Si controlla in UN SOLO POSTO, generaFiglioCon, che e' l'unico punto da cui nasce un figlio.
     * Nella maschera delle azioni NON c'e': un'entita' non deve sapere che il mondo e' pieno,
     * perche' sarebbe l'ennesima informazione regalata al posto di una percepita.
     */
    public static int limiteDiSicurezza() {
        int dim = world.getDim();
        return Math.max(600, dim * dim);
    }

    /** Quante mele far nascere a ogni intervallo, scalato sull'area (1 ogni 400 celle circa). */
    private static int appleSpawnBatch() {
        int dim = world.getDim();
        return Math.max(1, (int) Math.round((dim * dim) / 400.0));
    }

    private static void spawnApples() {
        if (tickCount % APPLE_INTERVAL_TICKS != 0) {
            return;
        }
        int cap = maxApples();
        int toSpawn = appleSpawnBatch();
        for (int spawned = 0; spawned < toSpawn && world.countApples() < cap; spawned++) {
            for (int attempt = 0; attempt < 10; attempt++) {
                int[] col = world.randomLandColumn(rng);
                int y = world.surfaceY(col[0], col[1]);
                if (!"M".equals(world.getSymbol(col[0], y, col[1]))) {
                    world.add(col[0], y, col[1], "M");
                    break;
                }
            }
        }
    }
}
