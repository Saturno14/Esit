package src.body;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * IL PALCO: la fisica viva dentro la simulazione, per poche entita' alla volta.
 *
 * COS'E' E PERCHE' NON SONO TUTTE. Fino a qui la fisica era un ORACOLO: un corpo nuovo veniva
 * fatto camminare in Jolt una volta, in un mondo a parte, e dalla misura usciva un numero
 * (metri al secondo) con cui la simulazione poi muoveva l'entita' a passi geometrici. Il corpo
 * contava, ma non si muoveva mai davvero. Farli camminare tutti e' fuori portata ed e' misurato:
 * 380 corpi con tutti i giunti comandati costano 1.93 ms per passo di fisica, cioe' circa 116 ms
 * per tick di mondo contro i 10 disponibili a x50 (vedi PhysicsBench e il TODO).
 *
 * Il palco e' la via di mezzo: un mondo fisico vero, sempre vivo, che ospita poche entita' alla
 * volta - quella SELEZIONATA (cosi' la si guarda camminare per davvero nella finestra 3D) e
 * quelle che stanno ancora imparando. Per chi e' sul palco il passo non e' piu' una formula: e'
 * la distanza che la ragdoll ha effettivamente coperto in quel mezzo secondo di mondo. Se cade,
 * non avanza. Il conto torna: 60 passi di fisica per tick per quattro corpi sono circa 1.2 ms,
 * dentro il budget anche a x50.
 *
 * COSA NON FA ANCORA, ed e' meglio scriverlo che scoprirlo dopo:
 *  - IL PAVIMENTO E' PIATTO. Il terreno vero (la mappa delle altezze) non e' ancora nel mondo
 *    fisico, quindi il palco dice come un corpo cammina, non come affronta un gradino. Le entita'
 *    continuano a seguire la quota del terreno come prima (vedi entity.syncCell).
 *  - NON SI RIALZA DA SOLO. Un corpo caduto resta a terra per {@link #TICK_A_TERRA} tick e poi
 *    viene rimesso in piedi. Un controllore che si rialza (e che mette un piede avanti quando
 *    cade) e' il progetto di cui parla il TODO: senza di quello, rialzarsi e' una concessione del
 *    mondo, non un'abilita' del corpo.
 *  - OGNUNO SUL SUO CORRIDOIO. Le entita' sul palco camminano su corsie parallele distanti
 *    {@link #CORSIA} metri e non si incontrano: del mondo vero il palco riproduce il corpo e la
 *    gravita', non la geografia.
 *
 * L'interruttore e' {@link src.ActionWeights#fisicaViva}: quante entita' stanno sul palco (0 =
 * palco spento, tutto come prima, ed e' il valore predefinito finche' il confronto appaiato non
 * dice che conviene).
 */
public final class Palco {

    /** Distanza tra due corsie (m): abbastanza perche' due corpi non si urtino mai. */
    private static final double CORSIA = 8.0;

    /** Quanti tick un corpo caduto resta a terra prima di essere rimesso in piedi. */
    public static volatile int TICK_A_TERRA = 6;

    /** Sotto questa frazione dell'altezza di partenza il corpo e' caduto (come GaitEvaluator). */
    private static final double FALL_RATIO = GaitEvaluator.FALL_RATIO;

    /** Un posto sul palco: una ragdoll viva, il suo oscillatore e quanto ha camminato. */
    private static final class Posto {
        final long id;
        final Automaton body;
        final int corsia;
        JoltRagdoll doll;
        Gait gait;
        /** I sensi di questo corpo: si costruiscono una volta, si rileggono a ogni passo. */
        MotorSense sensi;
        double fase;
        double altezzaIniziale;
        /** Metri percorsi e non ancora consumati da un'azione di movimento. */
        double metriDisponibili;
        V3 ultimoCom;
        int tickATerra = -1;
        float[] angoli;

        Posto(long id, Automaton body, int corsia) {
            this.id = id;
            this.body = body;
            this.corsia = corsia;
        }
    }

    private static PhysicsWorld mondo;
    private static boolean fisicaRotta = false;
    private static final Map<Long, Posto> posti = new LinkedHashMap<>();
    private static int capienzaAllestita = 0;
    private static long passiFisica = 0;
    private static long cadute = 0;
    private static double metriTotali = 0;

    private Palco() {
    }

    // ------------------------------------------------------------------ allestimento

    /**
     * Decide chi sta sul palco in questo tick: prima l'entita' indicata come preferita (quella
     * selezionata nella finestra 3D, cosi' si guarda camminare davvero), poi gli altri nell'ordine
     * in cui arrivano - chi li ordina e' il chiamante, che sa chi sta imparando.
     *
     * Chi esce dalla lista scende dal palco e la sua ragdoll viene smontata: tenere in piedi un
     * corpo che nessuno guarda e di cui nessuno usa il passo e' solo calcolo buttato.
     *
     * @param capienza quante entita' al massimo (0 = palco spento)
     * @param candidati entita' da mettere sul palco, in ordine di priorita'
     */
    public static synchronized void allestisci(int capienza, List<Candidato> candidati) {
        if (capienza <= 0 || fisicaRotta) {
            if (!posti.isEmpty()) {
                svuota();
            }
            return;
        }
        if (!apriMondo(capienza)) {
            return;
        }
        List<Long> tenuti = new ArrayList<>(capienza);
        for (Candidato c : candidati) {
            if (tenuti.size() >= capienza) {
                break;
            }
            tenuti.add(c.id());
            Posto p = posti.get(c.id());
            if (p == null) {
                p = new Posto(c.id(), c.body(), corsiaLibera());
                posti.put(c.id(), p);
            }
            // l'andatura puo' essere cambiata (allenamento, misura arrivata): il palco usa sempre
            // quella che l'entita' sta usando adesso
            if (p.gait != c.gait()) {
                p.gait = c.gait();
                smonta(p);
            }
            if (p.doll == null) {
                monta(p);
            }
        }
        posti.keySet().removeIf(id -> {
            if (tenuti.contains(id)) {
                return false;
            }
            smonta(posti.get(id));
            return true;
        });
    }

    /** Quello che serve al palco di un'entita': chi e', con che corpo e con che andatura. */
    public record Candidato(long id, Automaton body, Gait gait) {
    }

    private static int corsiaLibera() {
        int c = 0;
        boolean occupata = true;
        while (occupata) {
            occupata = false;
            for (Posto p : posti.values()) {
                if (p.corsia == c) {
                    occupata = true;
                    c++;
                    break;
                }
            }
        }
        return c;
    }

    private static boolean apriMondo(int capienza) {
        if (mondo != null && capienzaAllestita >= capienza) {
            return true;
        }
        try {
            if (mondo != null) {
                svuota();
                mondo.close();
            }
            PhysicsNatives.require();
            // le corsie stanno tutte dentro il pavimento: mezzo lato = corsie * CORSIA
            int parti = PhysicsWorld.MAX_PARTS_PER_BODY * capienza + 8;
            mondo = new PhysicsWorld(parti, 0);
            mondo.addGround((float) (CORSIA * capienza + 20), 0f, 0.95f);
            capienzaAllestita = capienza;
            System.out.println("[palco] fisica viva accesa per " + capienza + " entita'");
            return true;
        } catch (Throwable t) {
            // senza i nativi di Jolt il palco non si apre: tutto il resto continua come prima
            System.out.println("[palco] fisica non disponibile (" + t + "): palco spento.");
            fisicaRotta = true;
            mondo = null;
            return false;
        }
    }

    private static void monta(Posto p) {
        try {
            p.doll = JoltRagdoll.spawn(mondo, p.body, p.corsia * CORSIA, 0, 0, 0,
                    1000 + p.corsia);
            p.angoli = p.gait == null ? null : p.gait.anglesAt(p.body, p.fase, null);
            if (p.angoli != null) {
                p.doll.setTargetAngles(p.angoli);
            }
            p.altezzaIniziale = p.doll.rootPosition().y();
            p.ultimoCom = p.doll.centerOfMass();
            p.sensi = new MotorSense(p.body);
            p.tickATerra = -1;
        } catch (Throwable t) {
            // un corpo che la fisica non regge non deve fermare il mondo: scende dal palco
            p.doll = null;
        }
    }

    private static void smonta(Posto p) {
        if (p != null && p.doll != null) {
            try {
                p.doll.remove();
            } catch (Throwable ignored) {
                // il mondo fisico sta chiudendo: non c'e' niente da salvare
            }
            p.doll = null;
        }
    }

    private static void svuota() {
        for (Posto p : posti.values()) {
            smonta(p);
        }
        posti.clear();
    }

    /** Toglie un'entita' dal palco (e' morta, o non interessa piu'). */
    public static synchronized void esce(long id) {
        Posto p = posti.remove(id);
        smonta(p);
    }

    // ------------------------------------------------------------------ il tick

    /**
     * Avanza la fisica di tutti i corpi sul palco di {@code secondi} di mondo, e mette da parte
     * quanto ognuno ha camminato.
     *
     * COME SI MISURA IL PASSO. Si guarda di quanto si e' spostato il baricentro in AVANTI (la
     * direzione in cui la ragdoll e' stata messa, +Z): lo spostamento laterale non e' un passo, e
     * quello indietro non va sottratto al credito, perche' barcollare non e' tornare indietro.
     * Un corpo caduto non accumula niente: e' la differenza vera tra camminare e non camminare.
     */
    public static synchronized void avanza(double secondi) {
        if (mondo == null || posti.isEmpty()) {
            return;
        }
        int passi = (int) Math.round(secondi / PhysicsWorld.TIME_STEP);
        if (passi <= 0) {
            return;
        }
        try {
            int ogniQuanti = Math.max(1, GaitEvaluator.CONTROL_EVERY);
            for (int i = 0; i < passi; i++) {
                for (Posto p : posti.values()) {
                    if (p.doll == null || p.gait == null) {
                        continue;
                    }
                    p.fase += PhysicsWorld.TIME_STEP;
                    // stesso passo di controllo dell'oracolo: cio' che un corpo impara nella
                    // misura deve valere identico qui, altrimenti si allena in un mondo e vive
                    // in un altro
                    if (p.sensi != null && i % ogniQuanti == 0) {
                        p.sensi.read(p.doll, 0, 2 * Math.PI * p.gait.frequency() * p.fase);
                    }
                    p.angoli = p.gait.targetAngles(p.body, p.fase, p.sensi, p.angoli);
                    p.doll.setTargetAngles(p.angoli);
                }
                mondo.step();
                passiFisica++;
            }
            for (Posto p : posti.values()) {
                leggi(p);
            }
        } catch (Throwable t) {
            System.out.println("[palco] la fisica viva si e' fermata (" + t + "): palco spento.");
            fisicaRotta = true;
            svuota();
        }
    }

    private static void leggi(Posto p) {
        if (p.doll == null) {
            return;
        }
        boolean caduto = p.altezzaIniziale > 1e-6
                && p.doll.rootPosition().y() / p.altezzaIniziale < FALL_RATIO;
        V3 com = p.doll.centerOfMass();
        if (!caduto && p.ultimoCom != null) {
            double avanti = com.z() - p.ultimoCom.z();
            if (avanti > 0 && Double.isFinite(avanti)) {
                p.metriDisponibili += avanti;
                metriTotali += avanti;
            }
        }
        p.ultimoCom = com;

        if (caduto) {
            if (p.tickATerra < 0) {
                cadute++;
                p.tickATerra = 0;
            } else {
                p.tickATerra++;
            }
            if (p.tickATerra >= TICK_A_TERRA) {
                // rimesso in piedi: vedi la nota sul rialzarsi nell'intestazione
                smonta(p);
                p.fase = 0;
                monta(p);
            }
        } else {
            p.tickATerra = -1;
        }
    }

    // ------------------------------------------------------------------ uso dalla simulazione

    public static synchronized boolean contiene(long id) {
        return posti.containsKey(id) && posti.get(id).doll != null;
    }

    /**
     * I metri che questa entita' ha camminato davvero e non ha ancora speso, fino a un massimo
     * di {@code tetto} (la falcata che stava per fare): quello che resta si usa al passo
     * successivo. Chi non e' sul palco riceve -1, e chi chiama usa il passo geometrico.
     *
     * IL TETTO SERVE. Senza di esso un'entita' lenta che non si muove per dieci tick accumulerebbe
     * un credito e poi farebbe un salto di tre metri in una sola azione: la fisica dice quanti
     * metri in quanto tempo, non in quante azioni.
     */
    public static synchronized double consuma(long id, double tetto) {
        Posto p = posti.get(id);
        if (p == null || p.doll == null) {
            return -1;
        }
        double preso = Math.min(p.metriDisponibili, Math.max(0, tetto));
        p.metriDisponibili -= preso;
        return preso;
    }

    public static synchronized String stats() {
        return String.format(Locale.ROOT,
                "palco: %d entita' in fisica viva, %d passi di fisica, %.1f m camminati davvero, "
                        + "%d cadute%s",
                posti.size(), passiFisica, metriTotali, cadute,
                fisicaRotta ? " (palco spento da un errore)" : "");
    }

    /** Chiude il mondo fisico del palco (fine sessione). */
    public static synchronized void chiudi() {
        svuota();
        if (mondo != null) {
            try {
                mondo.close();
            } catch (Throwable ignored) {
                // in chiusura non c'e' piu' niente da proteggere
            }
            mondo = null;
            capienzaAllestita = 0;
        }
    }
}
