package src;

import java.util.ArrayList;
import java.util.List;

/**
 * Serie storica della simulazione tenuta IN MEMORIA, per i grafici della GUI.
 *
 * PERCHE' NON SI LEGGE IL CSV. StatsLog scrive logs/stats-session-N.csv ogni 20 tick, e va bene
 * per analizzare una corsa a cose fatte. Per un grafico che si aggiorna mentre la simulazione
 * gira, rileggere e riparsare un file che cresce a ogni frame e' sia lento sia fragile (il file
 * viene scritto da un thread e letto da un altro, e una riga a meta' basta a rompere il grafico).
 * Qui i campioni restano in memoria, strutturati, e il grafico li legge senza toccare il disco.
 *
 * I MORTI SONO CUMULATIVI, non per intervallo. Il grafico calcola la differenza tra due campioni
 * quando vuole il tasso: dal cumulativo si ricava l'intervallo, dall'intervallo non si ricava il
 * cumulativo se un campione va perso. Tenere la grandezza che non perde informazione e derivare
 * l'altra e' sempre il verso giusto.
 */
public final class History {

    /** Un tick su quanti si prende un campione. */
    public static final int SAMPLE_INTERVAL = 10;
    /**
     * Quanti campioni si tengono: a un campione ogni 10 tick sono 300000 tick di storia, molto
     * piu' di qualunque sessione. Oltre, i piu' vecchi cadono.
     */
    private static final int MAX_SAMPLES = 30000;

    /** Le tre cause di morte che la simulazione produce (vedi entity.takeDamage e attack). */
    public static final String[] CAUSE = {"fame", "sete", "combattimento"};
    public static final int FAME = 0;
    public static final int SETE = 1;
    public static final int COMBATTIMENTO = 2;

    /** Un campione: fotografia dei numeri a un dato tick. */
    public static final class Sample {
        public final long tick;
        public final int popolazione;
        public final int nascite;
        /** Morti cumulative per causa, nell'ordine di {@link #CAUSE}. */
        public final int[] morti;
        public final int mele;
        public final int alberi;
        public final double luce;

        Sample(long tick, int popolazione, int nascite, int[] morti,
               int mele, int alberi, double luce) {
            this.tick = tick;
            this.popolazione = popolazione;
            this.nascite = nascite;
            this.morti = morti;
            this.mele = mele;
            this.alberi = alberi;
            this.luce = luce;
        }

        /** Totale dei morti fino a questo tick. */
        public int mortiTotali() {
            return morti[FAME] + morti[SETE] + morti[COMBATTIMENTO];
        }
    }

    private static final Object LOCK = new Object();
    private static final List<Sample> samples = new ArrayList<>();
    private static final int[] morti = new int[CAUSE.length];
    private static int nascite = 0;

    private History() {
    }

    /** Riporta a zero la storia: un NewStart comincia un grafico nuovo. */
    public static void reset() {
        synchronized (LOCK) {
            samples.clear();
            java.util.Arrays.fill(morti, 0);
            nascite = 0;
        }
    }

    /**
     * Normalizza la causa di morte in una delle categorie del grafico.
     *
     * La causa che l'entita' porta e' una stringa libera - "fame", "sete", "ucciso da #42" -
     * perche' per il log serve sapere CHI ha ucciso. Per il grafico serve la categoria, e il
     * numero dell'assassino non e' una categoria: tutto cio' che non e' fame o sete e' un
     * combattimento.
     */
    public static int categoria(String causa) {
        if (causa == null) {
            return COMBATTIMENTO;
        }
        if (causa.startsWith("fame")) {
            return FAME;
        }
        if (causa.startsWith("sete")) {
            return SETE;
        }
        return COMBATTIMENTO;
    }

    /** Da chiamare a ogni morte (vedi Simulation.kill). */
    public static void morte(String causa) {
        synchronized (LOCK) {
            morti[categoria(causa)]++;
        }
    }

    /** Da chiamare a ogni nascita. */
    public static void nascita() {
        synchronized (LOCK) {
            nascite++;
        }
    }

    /** Da chiamare dal ciclo centrale: prende un campione se e' il momento. */
    public static void campiona(long tick) {
        if (tick % SAMPLE_INTERVAL != 0) {
            return;
        }
        int pop = Entity_manager.Entity_count();
        int mele = world.countApples();
        int alberi = Flora.enabled ? Flora.countTrees() : 0;
        double luce = DayNight.daylight(tick);
        synchronized (LOCK) {
            samples.add(new Sample(tick, pop, nascite, morti.clone(), mele, alberi, luce));
            if (samples.size() > MAX_SAMPLES) {
                samples.remove(0);
            }
        }
    }

    /** Copia dei campioni, per chi disegna (gira su un altro thread). */
    public static List<Sample> snapshot() {
        synchronized (LOCK) {
            return new ArrayList<>(samples);
        }
    }

    /** Morti per causa fino adesso. */
    public static int[] mortiPerCausa() {
        synchronized (LOCK) {
            return morti.clone();
        }
    }

    public static int nasciteTotali() {
        synchronized (LOCK) {
            return nascite;
        }
    }
}
