package src.body;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * IL RIFLESSO: come un corpo reagisce a quello che sente di se' stesso, mentre si muove.
 *
 * E' un peso per ogni canale di {@link MotorSense} e per ogni grado di liberta'. La correzione
 * dell'angolo obiettivo di un giunto e'
 *
 *   correzione(giunto) = somma su tutti i canali di  peso(giunto, canale) * canale(giunto)
 *
 * e si somma a quello che dice l'oscillatore di {@link Gait}. Con tutti i pesi a zero il
 * comportamento e' esattamente quello dell'oscillatore da solo: nessuna partenza a freddo in cui
 * nessuno si muove, e tutto quello che il riflesso impara e' guadagno sopra il punto di partenza.
 *
 * NON C'E' NESSUNA STRATEGIA SCRITTA QUI DENTRO, ed e' il punto. La vecchia classe Balance
 * calcolava a mano il verso in cui muovere il giunto per rimettere il piede sotto il tronco, con
 * guadagni fissi uguali per bipedi, quadrupedi e serpenti: un controllore scritto da fuori, che
 * imponeva una strategia e con essa decideva quali corpi potevano funzionare. Qui i pesi partono a
 * zero e l'unico modo in cui diventano qualcosa e' che una prova di fisica sia andata meglio:
 * {@link GaitEvaluator} da' il punteggio, {@link Locomotion#practice} fa la salita della collina
 * durante la vita, {@link Gait#cross} incrocia, {@link GaitArchive} conserva tra le sessioni.
 * Non e' stato scritto nessun algoritmo di apprendimento nuovo: e' cambiato il vettore di
 * parametri su cui gira quello che c'era gia'.
 *
 * COSA PUO' SCOPRIRE. Un peso dal canale "contatto dell'arto opposto" all'anca e' l'alternanza del
 * passo; uno dal "beccheggio" al ginocchio e' il raddrizzarsi; uno dalla "velocita' del
 * beccheggio" e' lo smorzamento che anticipa la caduta; uno dall'orologio e' un'oscillazione
 * propria, cioe' un'andatura che il riflesso si genera da solo invece di ricevere. Nessuna di
 * queste cose e' preferita dal codice: sono tutte lo stesso numero in una posizione diversa.
 *
 * IL TETTO DELLA CORREZIONE c'e' per un motivo pratico misurato altrove: un motore a cui si chiede
 * un angolo lontanissimo accumula energia e spara la ragdoll via a decine di m/s (vedi
 * GaitEvaluator.MAX_PLAUSIBLE_SPEED). Il tetto non dice al riflesso COSA fare, gli dice solo di
 * non strappare: e' una proprieta' del muscolo, non una strategia.
 */
public final class Reflex {

    /** Tetto della correzione che un riflesso puo' chiedere, in radianti. */
    public static volatile double MAX_CORRECTION = 0.8;

    /** pesi[dof][canale], indicizzati per NOME del grado di liberta' come gli oscillatori. */
    private final Map<String, double[]> byDof;

    private Reflex(Map<String, double[]> byDof) {
        this.byDof = byDof;
    }

    /** Riflesso spento: tutti i pesi a zero, cioe' il solo oscillatore. E' il punto di partenza. */
    public static Reflex zero(Automaton body) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (Dof d : body.dofs()) {
            m.put(d.name, new double[MotorSense.CHANNELS]);
        }
        return new Reflex(m);
    }

    /**
     * Riflesso tutto casuale. Serve solo per il confronto: dice quanto vale partire da zero e
     * salire, invece di partire da un punto qualunque dello spazio dei pesi.
     */
    public static Reflex random(Automaton body, Random rng, double sigma) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (Dof d : body.dofs()) {
            double[] w = new double[MotorSense.CHANNELS];
            for (int c = 0; c < w.length; c++) {
                w[c] = rng.nextGaussian() * sigma;
            }
            m.put(d.name, w);
        }
        return new Reflex(m);
    }

    public double[] weights(String dofName) {
        return byDof.get(dofName);
    }

    public Map<String, double[]> all() {
        return java.util.Collections.unmodifiableMap(byDof);
    }

    /** Vero se non c'e' proprio niente da applicare: tutti i pesi a zero. */
    public boolean isZero() {
        for (double[] w : byDof.values()) {
            for (double v : w) {
                if (v != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ uso

    /**
     * Somma la correzione del riflesso agli angoli obiettivo, sul posto, tenendoli dentro i limiti
     * dei giunti.
     */
    public void apply(Automaton body, MotorSense sensi, float[] q) {
        List<Dof> dofs = body.dofs();
        for (int i = 0; i < dofs.size() && i < q.length; i++) {
            Dof d = dofs.get(i);
            double[] w = byDof.get(d.name);
            if (w == null) {
                continue;
            }
            double c = 0;
            for (int ch = 0; ch < MotorSense.CHANNELS && ch < w.length; ch++) {
                if (w[ch] != 0) {
                    c += w[ch] * sensi.channel(i, ch);
                }
            }
            if (c == 0) {
                continue;
            }
            c = Math.max(-MAX_CORRECTION, Math.min(MAX_CORRECTION, c));
            q[i] = (float) Math.max(d.lower, Math.min(d.upper, q[i] + c));
        }
    }

    // ------------------------------------------------------------------ evoluzione

    /**
     * Copia con alcuni pesi spostati a caso.
     *
     * Si muove una FRAZIONE dei pesi, non tutti, per lo stesso motivo per cui l'oscillatore fa
     * cosi' (vedi {@link Gait#perturb}): con una prova di fisica per volta, spostare
     * trecentocinquanta numeri insieme e' rumore, spostarne pochi e' una ricerca per coordinate.
     * Qui la frazione conta ancora di piu', perche' i pesi sono molti piu' dei parametri
     * dell'oscillatore.
     */
    public Reflex perturb(Automaton body, Random rng, double sigma, double fraction) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (Dof d : body.dofs()) {
            double[] mine = byDof.get(d.name);
            double[] w = new double[MotorSense.CHANNELS];
            if (mine != null) {
                System.arraycopy(mine, 0, w, 0, Math.min(mine.length, w.length));
            }
            for (int c = 0; c < w.length; c++) {
                if (fraction < 1.0 && rng.nextDouble() > fraction) {
                    continue;
                }
                w[c] += rng.nextGaussian() * sigma;
            }
            m.put(d.name, w);
        }
        return new Reflex(m);
    }

    /**
     * Incrocio: per ogni giunto si prende il riflesso INTERO da uno dei due genitori.
     *
     * Non si mescolano i singoli pesi di uno stesso giunto: i pesi di un giunto sono una cosa sola
     * (se un'anca ha imparato a reagire al contatto dell'altra zampa, lo ha imparato con quella
     * combinazione di pesi), e mescolarli mezzi di uno e mezzi dell'altro rompe la combinazione
     * senza provare niente di nuovo. E' lo stesso criterio dell'oscillatore.
     */
    public static Reflex cross(Reflex a, Reflex b, Automaton body, Random rng) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (Dof d : body.dofs()) {
            double[] wa = a == null ? null : a.byDof.get(d.name);
            double[] wb = b == null ? null : b.byDof.get(d.name);
            double[] pick = wa == null ? wb : wb == null ? wa : (rng.nextBoolean() ? wa : wb);
            double[] w = new double[MotorSense.CHANNELS];
            if (pick != null) {
                System.arraycopy(pick, 0, w, 0, Math.min(pick.length, w.length));
            }
            m.put(d.name, w);
        }
        return new Reflex(m);
    }

    /** Adatta a un corpo diverso: i giunti con lo stesso nome tengono i pesi, i nuovi partono a zero. */
    public Reflex adaptTo(Automaton body) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (Dof d : body.dofs()) {
            double[] mine = byDof.get(d.name);
            double[] w = new double[MotorSense.CHANNELS];
            if (mine != null) {
                System.arraycopy(mine, 0, w, 0, Math.min(mine.length, w.length));
            }
            m.put(d.name, w);
        }
        return new Reflex(m);
    }

    // ------------------------------------------------------------------ JSON

    /**
     * I pesi come oggetto JSON. I giunti con tutti i pesi a zero NON si scrivono: all'inizio sono
     * la maggioranza, e un archivio in cui ogni corpo porta trecento zeri diventa illeggibile e
     * grosso per niente.
     */
    public JSONObject toJson() {
        JSONObject root = new JSONObject();
        root.put("canali", MotorSense.CHANNELS);
        JSONArray arr = new JSONArray();
        for (Map.Entry<String, double[]> e : byDof.entrySet()) {
            boolean tuttiZero = true;
            for (double v : e.getValue()) {
                if (v != 0) {
                    tuttiZero = false;
                    break;
                }
            }
            if (tuttiZero) {
                continue;
            }
            JSONObject o = new JSONObject();
            o.put("dof", e.getKey());
            JSONArray pesi = new JSONArray();
            for (double v : e.getValue()) {
                pesi.put(Math.round(v * 1e4) / 1e4);
            }
            o.put("pesi", pesi);
            arr.put(o);
        }
        root.put("giunti", arr);
        return root;
    }

    public static Reflex fromJson(JSONObject obj) {
        Map<String, double[]> m = new LinkedHashMap<>();
        JSONArray arr = obj == null ? null : obj.optJSONArray("giunti");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSONArray pesi = o.optJSONArray("pesi");
                double[] w = new double[MotorSense.CHANNELS];
                if (pesi != null) {
                    for (int c = 0; c < pesi.length() && c < w.length; c++) {
                        w[c] = pesi.getDouble(c);
                    }
                }
                m.put(o.getString("dof"), w);
            }
        }
        return new Reflex(m);
    }

    // ------------------------------------------------------------------ diagnostica

    /** Quanti pesi non sono zero, e quanto sono grandi: dice se il riflesso ha imparato qualcosa. */
    public String stats() {
        int attivi = 0, totali = 0;
        double somma = 0, massimo = 0;
        for (double[] w : byDof.values()) {
            for (double v : w) {
                totali++;
                if (v != 0) {
                    attivi++;
                    somma += Math.abs(v);
                    massimo = Math.max(massimo, Math.abs(v));
                }
            }
        }
        return String.format(Locale.ROOT, "riflesso: %d pesi su %d attivi, |peso| medio %.3f max %.3f",
                attivi, totali, attivi > 0 ? somma / attivi : 0, massimo);
    }

    /**
     * Quanto pesa ogni CANALE in tutto il corpo: e' la risposta alla domanda che conta davvero,
     * cioe' SU COSA questo corpo ha imparato a basarsi. Due morfologie che hanno scoperto
     * strategie diverse si vedono qui, non nella velocita'.
     */
    public double[] pesoPerCanale() {
        double[] tot = new double[MotorSense.CHANNELS];
        for (double[] w : byDof.values()) {
            for (int c = 0; c < w.length && c < tot.length; c++) {
                tot[c] += Math.abs(w[c]);
            }
        }
        return tot;
    }
}
