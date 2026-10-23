package src;

/**
 * Il ciclo del giorno e della notte.
 *
 * QUANTO DURA UN GIORNO. Un tick vale mezzo secondo di mondo (Simulation.TICK_MS), quindi un
 * giorno vero sarebbe 172800 tick: piu' lungo di cento vite. La durata qui e' scelta sul metro
 * della VITA di un'entita', non su quello dell'orologio: con {@link #DAY_TICKS} tick al giorno e
 * una vita mediana attorno ai 1700 tick, un'entita' vede qualche giorno e qualche notte - cioe'
 * abbastanza perche' il buio sia un problema da risolvere e non una sfortuna che capita una volta.
 *
 * COME CAMBIA LA LUCE. Non a scatti: alba e tramonto sono graduali, la notte e il giorno pieni
 * durano piu' del passaggio. La formula e' un seno appiattito - mezzogiorno e mezzanotte stanno
 * sui plateau, le due transizioni sono brevi - perche' una luce che cambia linearmente non ha ne'
 * un vero giorno ne' una vera notte, e il gene della visione notturna non avrebbe mai un momento
 * in cui conta davvero.
 */
public final class DayNight {

    /** Durata di un giorno completo in tick. */
    public static volatile int DAY_TICKS = 600;

    /** Con false la luce resta sempre al massimo (per confronti A/B). */
    public static volatile boolean enabled = true;

    /**
     * Scostamento in tick dell'ora del giorno: serve a SPOSTARE l'ora senza spostare la
     * simulazione.
     *
     * L'alternativa sarebbe cambiare il contatore dei tick, ma quello e' l'eta' del mondo: tutto
     * vi e' appeso (eta' delle entita', maturazione delle mele, log, genealogia), e portarlo
     * avanti di mezza giornata per vedere la notte farebbe invecchiare tutto quanto. Con uno
     * scostamento si muove solo il sole, che e' quello che si voleva.
     */
    public static volatile long OFFSET = 0;

    private DayNight() {
    }

    /**
     * Porta l'ora del giorno a quella frazione (0 e 1 = mezzanotte, 0.25 = mezzogiorno),
     * lasciando il tick dove e'.
     */
    public static void setPhase(double frazione) {
        int d = Math.max(1, DAY_TICKS);
        double f = frazione - Math.floor(frazione);
        long voluto = Math.round(f * d);
        long adesso = Simulation.getTick() % d;
        OFFSET = ((voluto - adesso) % d + d) % d;
    }

    /** Luce ambientale adesso: 1 = mezzogiorno, 0 = mezzanotte. */
    public static double daylight() {
        return daylight(Simulation.getTick());
    }

    public static double daylight(long tick) {
        if (!enabled) {
            return 1.0;
        }
        double phase = phase(tick);
        // seno appiattito: giorno e notte pieni, passaggi brevi
        double s = Math.sin(phase * 2 * Math.PI);
        double shaped = Math.tanh(s * 2.2);
        return 0.5 + 0.5 * shaped;
    }

    /** Dove sta il giorno, da 0 (mezzanotte) a 1 (mezzanotte dopo). 0.25 = mezzogiorno. */
    public static double phase(long tick) {
        int d = Math.max(1, DAY_TICKS);
        return (((tick + OFFSET) % d) + d) % d / (double) d;
    }

    public static boolean isNight() {
        return daylight() < 0.35;
    }

    /** Nome della fase, per i log e la console. */
    public static String phaseName() {
        double light = daylight();
        if (light > 0.8) {
            return "giorno";
        }
        if (light > 0.35) {
            return "crepuscolo";
        }
        return "notte";
    }

    /**
     * Quanto lontano si vede, in frazione della propria vista: con il sole alto si vede tutto,
     * con il buio si scende verso {@link #NIGHT_FLOOR}, e il gene della visione notturna
     * recupera quel che e' stato perso. Chi ha visione notturna 1 vede di notte come di giorno;
     * chi ha 0 ci vede un quarto.
     *
     * Il pavimento non e' zero perche' una notte in cui non si vede NIENTE non e' un problema da
     * risolvere ma una condanna: nessuna strategia batte nessun'altra strategia nel buio totale,
     * e un gene su cui la selezione non puo' lavorare e' solo rumore nel genoma.
     */
    public static final double NIGHT_FLOOR = 0.25;

    public static double visibility(double nightVision) {
        double light = daylight();
        double nightSight = NIGHT_FLOOR + (1.0 - NIGHT_FLOOR) * clamp01(nightVision);
        return light + (1.0 - light) * nightSight;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /**
     * Direzione da cui arriva la luce, per il disegno: il sole gira attorno all'asse X del mondo
     * e di notte la sostituisce una luce lunare piu' bassa e piu' fredda.
     */
    public static float[] lightDirection() {
        double phase = phase(Simulation.getTick());
        double angle = phase * 2 * Math.PI;
        float x = (float) (-0.4 * Math.cos(angle));
        float y = (float) -Math.max(0.15, Math.abs(Math.sin(angle)));
        float z = (float) (-0.3 - 0.3 * Math.cos(angle));
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / len, y / len, z / len};
    }
}
