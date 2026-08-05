import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ricostruisce dall'esterno lo storico di un'entita', senza modificare src/entity.java.
 *
 * entity.java non espone getter per fame/salute, quindi:
 *  - posizione, fitness, reward netto, sesso, stato vivo/morto sono dati REALI (letti
 *    dai getter pubblici che gia' esistono);
 *  - l'azione compiuta ad ogni tick e' DEDOTTA confrontando la variazione di posizione
 *    e di reward tra un poll e l'altro, sulla base delle regole di reward gia' scritte
 *    in entity.GoLife() (mossa riuscita ~+1.0, mangiata ~+1.5, fermo ~+0.5, urto contro
 *    un bordo ~-9.5, presa a vuoto ~-1.5);
 *  - fame/salute stimate sono una SIMULAZIONE PARALLELA con la stessa regola nota
 *    (-5 di fame a tick, +50 quando mangia, salute -5 se fame a 0, +1 se fame >=85),
 *    non una lettura diretta: possono divergere leggermente dal valore reale interno
 *    se un tick viene perso tra due poll consecutivi.
 */
public class EntityTracker {

    public static class LogEntry {
        public final String time;
        public final String text;
        public LogEntry(String time, String text) { this.time = time; this.text = text; }
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_LOG = 400;

    public final int id;
    public int sex;
    public int[] lastPos;
    public double lastNetreward;
    public boolean everSeen = false;
    public boolean deadLogged = false;

    public int estFood = 100;
    public int estHealth = 100;

    private final List<LogEntry> log = new ArrayList<>();

    public EntityTracker(int id) {
        this.id = id;
    }

    public synchronized List<LogEntry> getLog() {
        return new ArrayList<>(log);
    }

    private void addLog(String text) {
        log.add(new LogEntry(LocalTime.now().format(TIME_FMT), text));
        while (log.size() > MAX_LOG) {
            log.remove(0);
        }
    }

    /**
     * Aggiorna il tracker con un nuovo campionamento pubblico dell'entita'.
     * Ritorna true se e' stato registrato un nuovo evento (utile per capire se
     * un pannello aperto va rinfrescato).
     */
    public synchronized boolean update(int[] pos, int sex, double netreward, boolean alive) {
        this.sex = sex;

        if (!everSeen) {
            everSeen = true;
            lastPos = pos;
            lastNetreward = netreward;
            addLog("Comparsa in (" + pos[0] + "," + pos[1] + "," + pos[2] + ") - sesso "
                    + (sex == 2 ? "femmina" : "maschio"));
            return true;
        }

        double deltaNet = netreward - lastNetreward;
        boolean posChanged = !Arrays.equals(pos, lastPos);

        boolean loggedSomething = false;

        // deltaNet ~ 0 significa che non e' passato un nuovo tick dall'ultimo poll: niente da registrare.
        if (Math.abs(deltaNet) > 0.05) {
            if (posChanged) {
                addLog(String.format("Mossa verso (%d,%d,%d)  [reward %+.1f]", pos[0], pos[1], pos[2], deltaNet));
                estFood -= 5;
            } else if (deltaNet >= 1.2) {
                addLog(String.format("Ha mangiato una mela  [reward %+.1f]", deltaNet));
                estFood = Math.min(100, estFood - 5 + 50);
            } else if (deltaNet <= -8.0) {
                addLog(String.format("Ha urtato un bordo del mondo  [reward %+.1f]", deltaNet));
                estFood -= 5;
            } else if (deltaNet <= -1.0) {
                addLog(String.format("Ha tentato di prendere ma non c'era nulla  [reward %+.1f]", deltaNet));
                estFood -= 5;
            } else {
                addLog(String.format("E' rimasta ferma  [reward %+.1f]", deltaNet));
                estFood -= 5;
            }

            estFood = Math.max(0, Math.min(100, estFood));
            if (estFood <= 0) {
                estHealth = Math.max(0, estHealth - 5);
            } else if (estFood >= 85) {
                estHealth = Math.min(100, estHealth + 1);
            }

            lastPos = pos;
            lastNetreward = netreward;
            loggedSomething = true;
        }

        if (!alive && !deadLogged) {
            addLog("Entita' deceduta");
            deadLogged = true;
            loggedSomething = true;
        }

        return loggedSomething;
    }
}
