package src;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Ciclo di simulazione CENTRALE: un unico thread fa avanzare tutte le entita' a ogni tick,
 * al posto di un thread per entita'. Vantaggi: nessuna race condition tra entita' che
 * mangiano/attaccano nello stesso istante, risultati riproducibili con un seed, e il tempo
 * si puo' accelerare o rallentare (setTimeScale).
 *
 * Il gene "speed" di ogni entita' non cambia piu' la durata di uno sleep ma un accumulatore:
 * a ogni tick l'entita' accumula speed / SPEED_DIVISOR punti azione e agisce quando arriva a 1
 * (vedi entity.advance()).
 */
public class Simulation {

    /** Durata di un tick a timeScale 1. */
    public static final long TICK_MS = 500;
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

    private static long seed = System.nanoTime();
    private static Random rng = new Random(seed);

    private static final List<Corpse> corpses = new ArrayList<>();
    private static final List<entity> pendingBirths = new ArrayList<>();

    private static int births = 0;
    private static int deathsStarved = 0;
    private static int deathsKilled = 0;

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
        births = 0;
        deathsStarved = 0;
        deathsKilled = 0;
    }

    public static void setTimeScale(double scale) {
        timeScale = Math.max(0.1, Math.min(200.0, scale));
    }

    public static double getTimeScale() {
        return timeScale;
    }

    public static long getTick() {
        return tickCount;
    }

    /** Usato dal Load per riprendere dal tick salvato. */
    public static void setTick(long tick) {
        tickCount = tick;
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

        int[] p = e.getPos();
        double meat = e.getStats().maxHealth * 1.5;
        synchronized (corpses) {
            corpses.add(new Corpse(e, p[0], p[1], p[2], meat, tickCount + CORPSE_TICKS));
        }
        if (cause.startsWith("fame")) {
            deathsStarved++;
        } else {
            deathsKilled++;
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
                        tick();
                    }
                }
            } catch (Throwable t) {
                System.out.println("Errore tick simulazione: " + t);
                t.printStackTrace();
            }
            long budgetMs = (long) (TICK_MS / timeScale);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            long sleepMs = Math.max(1, budgetMs - elapsedMs);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                if (!running || myGeneration != generation) {
                    break;
                }
            }
        }
    }

    private static void tick() {
        tickCount++;

        List<entity> alive = Entity_manager.snapshotAlive();
        Collections.shuffle(alive, rng); // ordine casuale: nessuna entita' e' sempre "prima"
        for (entity e : alive) {
            if (e.isAlive()) {
                e.advance();
            }
        }

        flushBirths();
        synchronized (corpses) {
            corpses.removeIf(c -> c.isConsumed() || tickCount >= c.getExpireTick());
        }
        spawnApples();
        TrainedBrains.checkCapture(tickCount); // no-op se non e' una simulazione di addestramento

        if (tickCount % TICKS_PER_DAY == 0) {
            world.advanceCycle();
        }
        if (tickCount % SUMMARY_INTERVAL_TICKS == 0) {
            StatsLog.record(tickCount, world.countApples(), corpsesSnapshot().size(),
                    births, deathsStarved, deathsKilled);
            System.out.println("[tick " + tickCount + "] entita'=" + Entity_manager.Entity_count()
                    + " mele=" + world.countApples()
                    + " cadaveri=" + corpsesSnapshot().size()
                    + " nascite=" + births
                    + " morti(fame/combattimento)=" + deathsStarved + "/" + deathsKilled
                    + " azioni=" + java.util.Arrays.toString(entity.actionCounts));
        }
        if (tickCount % 100 == 0) {
            Genealogy.save();
        }
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
        }
    }

    /** Numero massimo di mele in giro, scalato sull'area della mappa (vedi DENSITY_PER_CELL). */
    public static int maxApples() {
        int dim = world.getDim();
        return Math.max(60, (int) Math.round(dim * dim * DENSITY_PER_CELL));
    }

    /** Popolazione massima, scalata sull'area della mappa con la stessa densita' delle mele. */
    public static int maxPopulation() {
        int dim = world.getDim();
        return Math.max(60, (int) Math.round(dim * dim * DENSITY_PER_CELL));
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
