package src.body;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * IL PALCO: la fisica viva dentro la simulazione. Le gambe e le braccia si muovono DAVVERO, e i
 * metri che un'entita' percorre sono quelli che la sua ragdoll ha coperto, non una formula.
 *
 * COM'ERA PRIMA. La fisica era un ORACOLO: un corpo nuovo veniva fatto camminare una volta, in un
 * mondo a parte, e da quella misura usciva un numero (metri al secondo) con cui la simulazione poi
 * muoveva l'entita' a passi geometrici. Il corpo contava - una gamba piu' lunga dava un numero piu'
 * grande - ma non si muoveva mai. Quello che si vedeva nella finestra era un ciclo di oscillatori
 * disegnato, non un corpo che spinge contro il pavimento.
 *
 * QUANTE ENTITA' CI STANNO, e perche' non e' piu' un numero scelto a mano. Costa: 380 corpi con
 * tutti i giunti comandati sono circa 116 ms di fisica per tick di mondo. Il punto che mi era
 * sfuggito, e che teneva spenta la fisica viva, e' che quel costo va confrontato con il tempo che
 * un tick ha DAVVERO, e un tick vale mezzo secondo di mondo: a velocita' normale sono 500 ms, e
 * 116 ms ci stanno dentro quattro volte. Era "fuori portata" solo rispetto ai 10 ms di un tick a
 * x50. Quindi il palco prende il budget di tempo del tick e ci mette dentro quante entita' ci
 * stanno, MISURANDO quanto costa davvero invece di indovinarlo ({@link #costoPerCorpoMs}): a
 * velocita' normale ci sta tutto il mondo, a x50 ci sta poca gente - e la poca gente e' quella
 * che serve, l'entita' selezionata e le piu' giovani, che stanno ancora imparando.
 *
 * CHI SI TRASCINA STA FUNZIONANDO. Prima un corpo abbassato sotto il 60% della sua altezza
 * accumulava ZERO metri ed era rimesso in piedi dal mondo dopo qualche tick. Per un bipede che
 * crolla e' giusto; per un corpo con UN SOLO ARTO DI APPOGGIO, che si sposta tirandosi con l'arto
 * che ha, voleva dire non potersi muovere mai - e quella non era una proprieta' del corpo, era una
 * legge scritta qui dentro. Adesso i metri si contano a CHIUNQUE AVANZI, in qualunque posa, e "a
 * terra" vuol dire abbassato E FERMO: un collasso e' mancanza di progresso, non una postura.
 * (Misurato il 24/10/2026: quei corpi avanzavano a 0.84 m/s di mediana mentre venivano contati
 * come caduti otto volte su otto.)
 *
 * OGNUNO SUL SUO CORRIDOIO. Le entita' camminano su corsie parallele distanti {@link #CORSIA}
 * metri e non si incontrano: del mondo vero il palco riproduce il corpo e la gravita', non la
 * geografia, e due entita' che nel mondo sono lontane chilometri non devono prendersi a spallate
 * perche' qui sono vicine. Il pavimento cresce con la capienza.
 *
 * COSA NON FA ANCORA, scritto perche' non si scopra dopo:
 *  - IL PAVIMENTO E' PIATTO. Il terreno vero (la mappa delle altezze) non e' ancora nel mondo
 *    fisico, quindi il palco dice come un corpo cammina, non come affronta un gradino. Le entita'
 *    continuano a seguire la quota del terreno come prima (vedi entity.syncCell).
 *  - NON SI RIALZA DA SOLO. Un corpo abbassato e fermo per {@link #TICK_A_TERRA} tick viene
 *    rimesso in piedi dal mondo. Rialzarsi e' un'abilita' da imparare, e per impararla serve che
 *    valga qualcosa nel punteggio: oggi non vale niente.
 *  - IL MONDO NON LO VEDE. Gli urti fra entita' (il combattimento) e le mele restano nella
 *    simulazione a griglia: la fisica muove le gambe, non risolve gli incontri.
 *
 * L'interruttore e' {@link src.ActionWeights#fisicaViva} (0 = spenta, 1 = accesa).
 */
public final class Palco {

    /**
     * Distanza tra due corsie (m): abbastanza perche' due corpi non si urtino mai.
     *
     * HO PROVATO A TOGLIERLE, mettendo tutte le entita' nello stesso gruppo di collisione perche'
     * si attraversassero e il pavimento potesse restare piccolo. NON SI PUO': la tabella dei
     * gruppi di Jolt rifiuta di spegnere la coppia (i, i) - "subgroups must be distinct" - quindi
     * due parti con lo stesso numero di sottogruppo si urtano sempre, e due entita' nello stesso
     * gruppo si prenderebbero a spallate a seconda di come il piano del corpo ha numerato i
     * cluster. Quindi: un gruppo per entita', corsie larghe, e il pavimento che cresce con la
     * capienza - costa una scatola statica, cioe' niente.
     */
    private static final double CORSIA = 8.0;
    /** Oltre questi metri percorsi si riparte dall'inizio della corsia (tapis roulant). */
    private static final double LONTANANZA = 400.0;

    /** Quanti tick un corpo abbassato E FERMO resta a terra prima di essere rimesso in piedi. */
    public static volatile int TICK_A_TERRA = 6;

    /** Sotto questa frazione dell'altezza di partenza il corpo e' abbassato (come GaitEvaluator). */
    private static final double FALL_RATIO = GaitEvaluator.FALL_RATIO;

    /**
     * Quanti metri in un tick contano come "si sta ancora muovendo". Mezzo secondo di mondo a
     * 4 cm/s: sotto questo, un corpo abbassato non sta strisciando, e' crollato.
     */
    private static final double PROGRESSO_MINIMO = 0.02;

    /** Tetto duro al numero di entita' in fisica (0 = nessuno, decide solo il budget di tempo). */
    public static volatile int tettoEntita = 0;

    /**
     * Senza budget: TUTTI in fisica, e il tick dura quanto deve durare.
     *
     * Il budget di tempo esiste per una ragione sola, stare al passo con chi guarda: a x50 un tick
     * ha 10 ms di tempo vero e la fisica di trecento corpi non ci sta, quindi ci salgono i piu'
     * importanti. In una corsa SENZA FINESTRA (src.SimRunner, i lotti di semi) non c'e' nessuno
     * che guarda e non c'e' nessun passo da tenere: il tick puo' durare mezzo secondo di calcolo
     * e il mondo e' identico, solo piu' lento da aspettare. Misurare un lotto col budget acceso
     * vorrebbe dire misurare sei entita' in fisica su trecento, cioe' non misurare la fisica.
     */
    public static volatile boolean senzaBudget = false;

    /**
     * Quanto costa un corpo per un tick di fisica, in millisecondi, misurato e aggiornato a ogni
     * tick (media esponenziale). E' questo numero che decide la capienza, invece di una costante
     * scritta a mano che sarebbe sbagliata su ogni macchina diversa da quella dove l'ho scritta.
     */
    private static volatile double costoPerCorpoMs = 0.3;

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
        /** Vero se in questo tick si e' mosso davvero. */
        boolean avanzato;
        /** Cosa l'entita' ha scelto di fare col corpo: vedi {@link Stato}. */
        Stato stato = Stato.FERMA;
        /** Se i motori sono spenti adesso: si cambia solo quando cambia, non a ogni passo. */
        boolean motoriSpenti;

        Posto(long id, Automaton body, int corsia) {
            this.id = id;
            this.body = body;
            this.corsia = corsia;
        }
    }

    /**
     * L'ISTANTANEA DI UN CORPO: dove stanno i suoi pezzi, alla fine di questo tick.
     *
     * Esiste per un motivo di thread, non di modello. La fisica gira nel thread della simulazione
     * e tiene il lucchetto del palco per tutti i sessanta passi del tick; il disegno gira nel suo
     * thread e chiede la posa a ogni fotogramma. Se il disegno leggesse la ragdoll vera, si
     * fermerebbe ad aspettare la fisica di ogni tick - a velocita' normale, centinaia di
     * millisecondi. Quindi la fisica, quando ha finito, lascia qui quello che ha prodotto, e il
     * disegno lo prende senza aspettare nessuno.
     *
     * NON E' UNA SECONDA IMMAGINE DA TENERE IN SINCRONIA: non c'e' niente da sincronizzare,
     * perche' qui non si calcola nulla. Sono le trasformate dei corpi rigidi copiate una volta
     * per tick, e l'oggetto e' immutabile: quando arriva il tick dopo, questo viene sostituito
     * intero.
     */
    private record Istantanea(JoltRagdoll corpo, V3[] posizioni, Rot[] rotazioni, V3 perno,
                             double imbardata) {
    }

    private static PhysicsWorld mondo;
    private static boolean fisicaRotta = false;
    private static final Map<Long, Posto> posti = new LinkedHashMap<>();
    /** Le istantanee per il disegno: scritte dalla fisica, lette dal disegno senza lucchetti. */
    private static final java.util.concurrent.ConcurrentHashMap<Long, Istantanea> istantanee =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static int capienzaMondo = 0;
    private static int capienzaUltima = 0;
    private static long passiFisica = 0;
    private static long collassi = 0;
    private static long montaggiFalliti = 0;
    private static double metriTotali = 0;

    private Palco() {
    }

    // ------------------------------------------------------------------ allestimento

    /**
     * Decide chi sta sul palco in questo tick: prima l'entita' preferita dal chiamante (quella
     * selezionata nella finestra 3D, cosi' si guarda camminare davvero), poi le altre nell'ordine
     * in cui arrivano - chi le ordina e' il chiamante, che sa chi sta imparando.
     *
     * Chi esce dalla lista scende dal palco e la sua ragdoll viene smontata: tenere in piedi un
     * corpo di cui nessuno usa il passo e' solo calcolo buttato.
     *
     * @param candidati entita' da mettere sul palco, in ordine di priorita'
     * @param budgetMs  quanti millisecondi di calcolo questo tick puo' dare alla fisica. Da qui
     *                  esce la capienza: quante entita' ci stanno dentro al costo misurato.
     */
    public static synchronized void allestisci(List<Candidato> candidati, double budgetMs) {
        if (budgetMs <= 0 || fisicaRotta || candidati.isEmpty()) {
            if (!posti.isEmpty()) {
                svuota();
            }
            return;
        }
        int capienza = capienzaPer(budgetMs, candidati.size());
        if (!apriMondo(capienza)) {
            return;
        }
        capienzaUltima = capienza;
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
            p.stato = c.stato();
            if (p.doll == null) {
                monta(p);
            }
            boolean spenti = p.stato == Stato.DORME;
            if (p.doll != null && spenti != p.motoriSpenti) {
                try {
                    p.doll.motoriSpenti(spenti);
                    p.motoriSpenti = spenti;
                } catch (Throwable ignored) {
                    // un corpo che non accetta il cambio di stato resta come sta: non e' un
                    // motivo per fermare il mondo
                }
            }
        }
        posti.keySet().removeIf(id -> {
            if (tenuti.contains(id)) {
                return false;
            }
            smonta(posti.get(id));
            istantanee.remove(id);
            return true;
        });
    }

    /**
     * In che stato il corpo riceve comandi. Sono tre perche' tre sono le cose diverse che
     * un'entita' puo' fare col proprio corpo, e appiattirle sarebbe mentire su una di esse.
     */
    public enum Stato {
        /** Ha scelto di muoversi: l'andatura avanza, le gambe si muovono. */
        CAMMINA,
        /** Sveglia ma ferma (mangia, si gira, guarda): i motori tengono la posa che hanno. */
        FERMA,
        /**
         * DORME: nessun comando al corpo, motori spenti. Se cade, cade - ed e' giusto che si
         * veda. Un'entita' che dorme non sta tenendo la posizione, sta dormendo.
         */
        DORME
    }

    /**
     * Quello che serve al palco di un'entita': chi e', con che corpo, con che andatura e in che
     * stato.
     *
     * Lo stato serve perche' il corpo faccia quello che l'entita' ha DECISO e non quello che gli
     * viene per inerzia. E' la scelta del tick PRECEDENTE (il palco avanza prima che le entita'
     * agiscano, altrimenti spenderebbero i metri del tick prima), quindi fra la decisione e il
     * corpo c'e' un tick di ritardo: mezzo secondo di mondo, meno del tempo che un passo dura.
     */
    public record Candidato(long id, Automaton body, Gait gait, Stato stato) {
    }

    /**
     * Quante entita' stanno dentro al budget. Almeno una sempre: se c'e' tempo per un solo corpo,
     * quel corpo e' il primo della lista - cioe' quello selezionato, che e' l'unico che qualcuno
     * sta guardando.
     */
    private static int capienzaPer(double budgetMs, int quanti) {
        double costo = Math.max(1e-3, costoPerCorpoMs);
        int stanno = senzaBudget ? quanti : (int) Math.floor(budgetMs / costo);
        int cap = Math.max(1, Math.min(quanti, stanno));
        if (tettoEntita > 0) {
            cap = Math.min(cap, tettoEntita);
        }
        return cap;
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

    /**
     * Apre il mondo fisico, o lo riapre piu' grande se serve.
     *
     * CRESCE E NON RIMPICCIOLISCE, a scatti di 64 entita': Jolt prealloca i corpi, quindi cambiare
     * dimensione vuol dire ricreare il mondo e rimontare tutte le ragdoll, perdendo le fasi e i
     * metri messi da parte. A scatti grossi succede qualche volta mentre la popolazione cresce e
     * poi mai piu'; a ogni tick sarebbe un massacro.
     */
    private static boolean apriMondo(int capienza) {
        if (mondo != null && capienzaMondo >= capienza) {
            return true;
        }
        int nuova = ((capienza + 63) / 64) * 64;
        try {
            if (mondo != null) {
                svuota();
                mondo.close();
            }
            PhysicsNatives.require();
            int parti = PhysicsWorld.MAX_PARTS_PER_BODY * nuova + 8;
            mondo = new PhysicsWorld(parti, 0);
            // il pavimento copre tutte le corsie piu' la distanza del tapis roulant
            mondo.addGround((float) (CORSIA * nuova + LONTANANZA + 20), 0f, 0.95f);
            capienzaMondo = nuova;
            System.out.println("[palco] fisica viva: mondo per " + nuova + " entita'");
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
            // un gruppo di collisione per corsia: due entita' non si incontrano mai, quindi il
            // gruppo puo' essere il numero di corsia
            p.doll = JoltRagdoll.spawn(mondo, p.body, p.corsia * CORSIA, 0, 0, 0, 1 + p.corsia);
            p.angoli = p.gait == null ? null : p.gait.anglesAt(p.body, p.fase, null);
            if (p.angoli != null) {
                p.doll.setTargetAngles(p.angoli);
            }
            p.altezzaIniziale = p.doll.rootPosition().y();
            p.ultimoCom = p.doll.centerOfMass();
            p.sensi = new MotorSense(p.body);
            p.tickATerra = -1;
        } catch (Throwable t) {
            // un corpo che la fisica non regge non deve fermare il mondo: scende dal palco, e si
            // conta, perche' un palco che fallisce in silenzio sembrerebbe un palco che funziona
            p.doll = null;
            montaggiFalliti++;
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
        istantanee.clear();
    }

    /** Toglie un'entita' dal palco (e' morta, o non interessa piu'). */
    public static synchronized void esce(long id) {
        Posto p = posti.remove(id);
        istantanee.remove(id);
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
     * CHIUNQUE AVANZI ACCUMULA, in qualunque posa: vedi la nota sul trascinarsi nell'intestazione.
     */
    public static synchronized void avanza(double secondi) {
        if (mondo == null || posti.isEmpty()) {
            return;
        }
        int passi = (int) Math.round(secondi / PhysicsWorld.TIME_STEP);
        if (passi <= 0) {
            return;
        }
        long t0 = System.nanoTime();
        int corpi = 0;
        try {
            int ogniQuanti = Math.max(1, GaitEvaluator.CONTROL_EVERY);
            for (int i = 0; i < passi; i++) {
                for (Posto p : posti.values()) {
                    if (p.doll == null || p.gait == null) {
                        continue;
                    }
                    /*
                     * LA FASE SCORRE SOLO SE L'ENTITA' HA SCELTO DI MUOVERSI. Ferma, i motori
                     * continuano a tenere la posa che avevano - cioe' il corpo prova a stare su
                     * senza camminare, che e' quello che "riposa" vuol dire per un corpo dentro
                     * la gravita'. Senza questo le gambe si muoverebbero sempre, per tutti, anche
                     * per chi sta mangiando, e lo spostamento non sarebbe piu' la conseguenza di
                     * una decisione.
                     */
                    if (p.stato == Stato.CAMMINA) {
                        p.fase += PhysicsWorld.TIME_STEP;
                    }
                    // stesso passo di controllo dell'oracolo: cio' che un corpo impara nella
                    // misura deve valere identico qui, altrimenti si allena in un mondo e vive
                    // in un altro
                    if (p.sensi != null && i % ogniQuanti == 0) {
                        p.sensi.read(p.doll, 0, 2 * Math.PI * p.gait.frequency() * p.fase);
                    }
                    if (p.stato != Stato.DORME) {
                        p.angoli = p.gait.targetAngles(p.body, p.fase, p.sensi, p.angoli);
                        p.doll.setTargetAngles(p.angoli);
                    }
                }
                mondo.step();
                passiFisica++;
            }
            for (Posto p : posti.values()) {
                if (p.doll != null) {
                    corpi++;
                }
                leggi(p);
                istantanea(p);
            }
        } catch (Throwable t) {
            System.out.println("[palco] la fisica viva si e' fermata (" + t + "): palco spento.");
            fisicaRotta = true;
            svuota();
            return;
        }
        if (corpi > 0) {
            double ms = (System.nanoTime() - t0) / 1e6 / corpi;
            // media esponenziale: un tick lento perche' la macchina era occupata non deve
            // dimezzare la capienza del mondo
            costoPerCorpoMs = 0.9 * costoPerCorpoMs + 0.1 * ms;
        }
    }

    private static void leggi(Posto p) {
        if (p.doll == null) {
            return;
        }
        V3 com = p.doll.centerOfMass();
        double avanti = p.ultimoCom == null ? 0 : com.z() - p.ultimoCom.z();
        if (!Double.isFinite(avanti)) {
            avanti = 0;
        }
        /*
         * SI ACCUMULA SOLO QUELLO CHE SI E' DECISO DI FARE. Trovato da PalcoSelfTest: un corpo
         * con i motori fermi SI RIBALTA IN AVANTI E SCIVOLA, e contando qualunque spostamento in
         * avanti un'entita' che riposava copriva piu' strada di una che camminava (1.32 m contro
         * 1.09 in dieci secondi di mondo). Cioe' metteva da parte credito CADENDO, e lo spendeva
         * dopo come se avesse camminato.
         * Non e' un giudizio sulla posa - chi si trascina accumula quanto chiunque altro, ed e'
         * tutto il punto della nota sul trascinarsi qui sopra - e' che un movimento che non hai
         * deciso di fare non e' un tuo passo. Nel mondo fisico il corpo cade comunque, e la
         * caduta si vede; solo non diventa strada percorsa.
         */
        if (avanti > 0 && p.stato == Stato.CAMMINA) {
            p.metriDisponibili += avanti;
            metriTotali += avanti;
        }
        p.avanzato = p.stato == Stato.CAMMINA && avanti > PROGRESSO_MINIMO;
        p.ultimoCom = com;

        boolean basso = p.altezzaIniziale > 1e-6
                && p.doll.rootPosition().y() / p.altezzaIniziale < FALL_RATIO;
        /*
         * COLLASSO = ABBASSATO E FERMO. Non "abbassato": chi striscia e chi si trascina sta
         * abbassato per tutta la vita e si sta muovendo. E' la stessa correzione fatta nel
         * punteggio di GaitEvaluator (CRAWL_WEIGHT, UPRIGHT_BONUS), qui nel mondo vivo.
         */
        if (basso && !p.avanzato && p.stato != Stato.DORME) {
            if (p.tickATerra < 0) {
                collassi++;
                p.tickATerra = 0;
            } else {
                p.tickATerra++;
            }
            if (p.tickATerra >= TICK_A_TERRA) {
                // rimesso in piedi: vedi la nota sul rialzarsi nell'intestazione
                ricomincia(p);
            }
        } else {
            /*
             * CHI E' SVEGLIO SI RIALZA, CHI DORME RESTA DOV'E' CADUTO.
             *
             * Rialzarsi e' una concessione del mondo e non un'abilita' del corpo (vedi la nota
             * nell'intestazione): dopo TICK_A_TERRA tick abbassato e fermo, la ragdoll viene
             * rimontata in piedi. Dormendo non succede, perche' tirare su un corpo che dorme ogni
             * tre secondi sarebbe il mondo che gli rifa' il letto, e nasconderebbe proprio la
             * cosa che si vuole vedere.
             *
             * MISURATO perche' prima la concessione valeva solo per chi stava provando a
             * camminare: con il controllo di oggi crolla anche chi sta fermo sveglio, quindi chi
             * cadeva mentre mangiava restava a terra per sempre, e le nascite su tre semi
             * scendevano da 63/100/107 a 37/40/30. Un'entita' sveglia che e' per terra prova a
             * tirarsi su: e' il minimo che un animale fa.
             */
            p.tickATerra = -1;
        }

        /*
         * TAPIS ROULANT: chi ha camminato tanto da arrivare al bordo del pavimento riparte
         * dall'inizio della sua corsia. Succede dopo centinaia di metri, cioe' quasi mai, e un
         * corpo che cammina cosi' bene ha gia' dimostrato quello che c'era da dimostrare.
         */
        if (Math.abs(com.z()) > LONTANANZA) {
            ricomincia(p);
        }
    }

    /** Lascia al disegno quello che la fisica ha appena prodotto. Vedi {@link Istantanea}. */
    private static void istantanea(Posto p) {
        if (p.doll == null) {
            istantanee.remove(p.id);
            return;
        }
        try {
            int n = p.doll.clusterCount();
            V3[] pos = new V3[n];
            Rot[] rot = new Rot[n];
            p.doll.leggiTrasformate(pos, rot);
            istantanee.put(p.id, new Istantanea(p.doll, pos, rot,
                    p.doll.rootPosition(), p.doll.yawRoot()));
        } catch (Throwable t) {
            istantanee.remove(p.id);
        }
    }

    private static void ricomincia(Posto p) {
        smonta(p);
        p.fase = 0;
        monta(p);
    }

    // ------------------------------------------------------------------ uso dalla simulazione

    public static synchronized boolean contiene(long id) {
        Posto p = posti.get(id);
        return p != null && p.doll != null;
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

    /**
     * Gli angoli VERI dei giunti di questa entita', come stanno adesso nel mondo fisico.
     *
     * Serve alla grafica, ed e' la differenza fra vedere la fisica e sentirsi dire che c'e': se il
     * palco e' vivo, la posa disegnata deve essere quella che la fisica ha prodotto - braccia e
     * gambe che si muovono perche' spingono contro il pavimento, non perche' il seno di un
     * oscillatore dice di muoverle. Chi non e' sul palco continua a essere disegnato col ciclo.
     *
     * DENTRO I LIMITI DEI GIUNTI, e questo e' il motivo per cui il metodo esiste invece di
     * chiamare direttamente JoltRagdoll.jointAngles. Per i giunti a due e tre assi l'angolo
     * misurato e' una PROIEZIONE della rotazione relativa sull'asse, e non e' una decomposizione
     * esatta: in Jolt quei giunti diventano un solo vincolo a sei gradi con gli assi
     * ortogonalizzati, quindi la proiezione puo' dare un valore fuori dalla corsa del giunto.
     * Misurato con PalcoSelfTest: sforo fino a 1.8 rad, e SEMPRE su giunti a 3 gradi - mai su una
     * cerniera. Per una misura va bene saperlo; per il disegno no, perche' un arto piegato oltre
     * il suo limite sullo schermo e' un'entita' rotta. Quindi: i sensi ({@link MotorSense}) usano
     * il valore grezzo, il disegno usa questo, tagliato.
     *
     * @return gli angoli, o null se questa entita' non e' sul palco
     */
    public static synchronized float[] angoliDi(long id, float[] out) {
        Posto p = posti.get(id);
        if (p == null || p.doll == null) {
            return null;
        }
        try {
            float[] q = p.doll.jointAngles(out);
            List<Dof> dofs = p.body.dofs();
            for (int i = 0; i < q.length && i < dofs.size(); i++) {
                Dof d = dofs.get(i);
                q[i] = (float) Math.max(d.lower, Math.min(d.upper, q[i]));
            }
            return q;
        } catch (Throwable t) {
            return null;
        }
    }

    public static BodyPose posaDi(long id, double facing) {
        /*
         * NON E' synchronized, ed e' il punto: la chiama il thread del disegno a ogni fotogramma,
         * e il thread della fisica tiene il lucchetto di questa classe per tutti i sessanta passi
         * di un tick. Qui si legge solo l'istantanea, che e' immutabile e arriva da una mappa
         * concorrente, e si compone la posa - puro calcolo, nessun accesso al mondo fisico.
         */
        Istantanea i = istantanee.get(id);
        if (i == null) {
            return null;
        }
        try {
            BodyPose posa = i.corpo().posaDa(i.posizioni(), i.rotazioni());
            return posa.transformed(Rot.yaw(facing - i.imbardata()),
                    new V3(i.perno().x(), 0, i.perno().z()));
        } catch (Throwable t) {
            return null;
        }
    }

    public static synchronized String stats() {
        return String.format(Locale.ROOT,
                "palco: %d entita' in fisica viva (capienza %d, mondo per %d, %.2f ms per corpo), "
                        + "%d passi di fisica, %.1f m camminati davvero, %d collassi%s%s",
                posti.size(), capienzaUltima, capienzaMondo, costoPerCorpoMs, passiFisica,
                metriTotali, collassi,
                montaggiFalliti > 0 ? ", " + montaggiFalliti + " corpi che la fisica non regge" : "",
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
            capienzaMondo = 0;
        }
    }
}
