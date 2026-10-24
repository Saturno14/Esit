package src.body;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IL MONDO FISICO: le entita' camminano nel mondo che hanno davanti.
 *
 * Non c'e' piu' un palco. Prima la fisica era un posto a parte - un pavimento piatto, corsie
 * parallele a otto metri, un corpo per corsia, un tapis roulant a quattrocento metri - e il mondo
 * prendeva da lì dei METRI che l'entita' spendeva per spostarsi sulla griglia. Il corpo si muoveva
 * davvero, ma non qui: camminava su un tapis roulant mentre l'entita' camminava altrove, e le due
 * cose stavano insieme perche' un numero passava dall'una all'altra.
 *
 * ADESSO C'E' UN MONDO SOLO. La ragdoll di ogni entita' sta dove l'entita' sta, sul terreno vero
 * con le sue buche e i suoi dossi, dentro l'acqua se l'entita' e' in acqua, e urta gli alberi e le
 * altre entita'. La posizione dell'entita' non e' piu' una cosa che la simulazione SCRIVE: e' una
 * cosa che LEGGE dal proprio corpo. Se il corpo inciampa, l'entita' non avanza; se non riesce a
 * salire un dosso, resta sotto; se e' troppo lenta per arrivare al cibo, muore.
 *
 * COSA E' SPARITO, e sono tutte cose che tenevano in piedi la finzione:
 *  - le corsie e il tapis roulant (CORSIA, LONTANANZA): non c'e' piu' un corridoio, c'e' il mondo;
 *  - il credito di movimento (metri e radianti messi da parte e poi spesi): il corpo non consegna
 *    piu' spostamenti a un'entita' che sta altrove, il corpo E' dove l'entita' sta;
 *  - la CAPIENZA. Il palco teneva in fisica quante entita' stavano in un budget di tempo e
 *    lasciava le altre al passo geometrico: due classi di entita' con due leggi diverse, nello
 *    stesso mondo. Adesso o ci sono tutte o non c'e' la fisica. Il prezzo e' che il tick dura
 *    quanto deve durare: a popolazioni alte la simulazione va piu' lenta del tempo reale, e non
 *    c'e' modo di avere insieme un mondo fisico e un mondo veloce.
 *
 * COSA RESTA DEL PALCO, perche' era giusto:
 *  - i tre stati del corpo ({@link Stato}): la fase scorre solo se l'entita' ha scelto di
 *    muoversi, e chi dorme ha i motori SPENTI, quindi cade e si vede cadere;
 *  - l'istantanea per il disegno ({@link Istantanea}), che non e' una seconda immagine da tenere
 *    in sincronia ma le trasformate copiate una volta per tick;
 *  - nessuno rialza nessuno: chi cade resta caduto (voce 24 del diario);
 *  - un corpo che la fisica non regge scende e si conta, invece di fermare il mondo.
 *
 * L'interruttore e' {@link src.ActionWeights#fisicaViva} (0 = spenta, 1 = accesa).
 */
public final class MondoFisico {

    /** Cosa l'entita' sta facendo col proprio corpo. */
    public enum Stato {
        /** Si sta muovendo: la fase dell'andatura scorre e lo sterzo arriva ai motori. */
        CAMMINA,
        /** Sveglia e ferma: i motori tengono la posa, cioe' prova a stare su senza camminare. */
        FERMA,
        /** Dorme: i motori sono spenti. Se cade, cade - e non e' un difetto, e' l'assenza di comandi. */
        DORME
    }

    /**
     * Quello che serve al mondo per un'entita'.
     *
     * La posizione serve SOLO alla prima messa in campo: dopo, dove l'entita' sta lo decide il
     * corpo, e la simulazione lo legge (vedi {@link #posizioneDi}). Passarla a ogni tick e
     * rimetterci il corpo sarebbe tornare a scrivere la posizione da fuori, cioe' annullare tutto.
     *
     * Lo stato e lo sterzo sono la scelta del tick PRECEDENTE (il mondo avanza prima che le entita'
     * agiscano), quindi fra la decisione e il corpo c'e' un tick di ritardo: mezzo secondo di
     * mondo, meno del tempo che un passo dura.
     */
    public record Candidato(long id, Automaton body, Gait gait, Stato stato, double sterzo,
                            double x, double z, double facing) {
    }

    /**
     * Dove un corpo e' arrivato, da leggere dopo {@link #avanza}.
     *
     * @param abbassato vero se il corpo sta sotto la frazione {@link GaitEvaluator#FALL_RATIO}
     *                  della sua altezza di partenza. E' una DESCRIZIONE della posa, non un
     *                  giudizio: chi striscia ci sta sotto per tutta la vita e sta funzionando.
     */
    public record Posizione(double x, double z, double facing, double quota, boolean abbassato) {
    }

    /** Un corpo nel mondo: la ragdoll viva, la sua andatura e cosa l'entita' le sta chiedendo. */
    private static final class Posto {
        final long id;
        final Automaton body;
        JoltRagdoll doll;
        Gait gait;
        /** I sensi di questo corpo: si costruiscono una volta, si rileggono a ogni passo. */
        MotorSense sensi;
        double fase;
        double altezzaIniziale;
        /** Dove mettere il corpo quando lo si monta la prima volta. */
        double x;
        double z;
        double facing;
        /** Lo sterzo che l'entita' chiede: -1 a sinistra, +1 a destra, 0 dritto. */
        double sterzo;
        float[] angoli;
        Stato stato = Stato.FERMA;
        /** Se i motori sono spenti adesso: si cambia solo quando cambia, non a ogni passo. */
        boolean motoriSpenti;
        /** Tick consecutivi abbassato e fermo (-1 = in piedi o in movimento). Solo diagnostica. */
        int tickATerra = -1;
        V3 ultimoCom;

        Posto(long id, Automaton body, double x, double z, double facing) {
            this.id = id;
            this.body = body;
            this.x = x;
            this.z = z;
            this.facing = facing;
        }
    }

    /**
     * L'ISTANTANEA DI UN CORPO: dove stanno i suoi pezzi alla fine di questo tick.
     *
     * Esiste per un motivo di thread, non di modello. La fisica gira nel thread della simulazione e
     * tiene il lucchetto per tutti i passi del tick; il disegno gira nel suo thread e chiede la
     * posa a ogni fotogramma. Se il disegno leggesse la ragdoll vera si fermerebbe ad aspettare la
     * fisica di ogni tick. Quindi la fisica, quando ha finito, lascia qui quello che ha prodotto.
     *
     * NON E' UNA SECONDA IMMAGINE DA TENERE IN SINCRONIA: qui non si calcola niente, sono le
     * trasformate dei corpi rigidi copiate una volta per tick, e l'oggetto e' immutabile.
     *
     * Il PERNO e' il punto rispetto a cui la posa viene consegnata al disegno: la posizione
     * orizzontale del corpo e la quota del TERRENO DELLA SUA CELLA, che e' quello che il motore
     * grafico somma di nuovo. Cosi' un corpo finito in una buca viene disegnato dentro la buca.
     */
    private record Istantanea(JoltRagdoll corpo, V3[] posizioni, Rot[] rotazioni, V3 perno,
                              Posizione dove) {
    }

    private static PhysicsWorld mondo;
    private static boolean fisicaRotta = false;
    private static final Map<Long, Posto> posti = new LinkedHashMap<>();
    /** Le istantanee per il disegno: scritte dalla fisica, lette dal disegno senza lucchetti. */
    private static final ConcurrentHashMap<Long, Istantanea> istantanee = new ConcurrentHashMap<>();
    private static int capienzaMondo = 0;
    private static long passiFisica = 0;
    private static long collassi = 0;
    private static long montaggiFalliti = 0;
    private static double metriTotali = 0;
    private static double costoPerCorpoMs = 0.3;

    /** Versione del terreno con cui il mondo fisico e' stato costruito: cambia, si ricostruisce. */
    private static int versioneTerreno = -1;
    private static int alberiNelMondo = -1;
    private static int corpoTerreno = -1;
    private static final List<Integer> corpiAlberi = new ArrayList<>();

    private MondoFisico() {
    }

    /** Attrito del terreno. Lo stesso del vecchio pavimento piatto, per non cambiare due cose insieme. */
    private static final float ATTRITO = 0.95f;

    /**
     * Ogni quanti passi di fisica si applica la spinta dell'acqua.
     *
     * Non a ogni passo: la spinta e' un impulso proporzionale al tempo, quindi applicarla ogni
     * quattro passi con un dt quadruplo da' la stessa spinta e costa un quarto. Quattro passi a
     * 120 Hz sono 30 Hz, e l'acqua non cambia in un trentesimo di secondo.
     */
    private static final int GALLEGGIO_OGNI = 4;

    // ------------------------------------------------------------------ messa in campo

    /**
     * Mette nel mondo fisico tutte le entita' vive, e togli quelle che non ci sono piu'.
     *
     * NESSUN BUDGET E NESSUNA CAPIENZA: o ci sono tutte o la fisica non ha senso (vedi
     * l'intestazione). Se il mondo e' troppo piccolo cresce; se un corpo non si regge, scende.
     */
    public static synchronized void allestisci(List<Candidato> candidati) {
        if (fisicaRotta) {
            return;
        }
        if (candidati.isEmpty()) {
            svuota();
            return;
        }
        if (!apriMondo(candidati.size())) {
            return;
        }
        terrenoAggiornato();

        List<Long> tenuti = new ArrayList<>(candidati.size());
        for (Candidato c : candidati) {
            tenuti.add(c.id());
            Posto p = posti.get(c.id());
            if (p == null) {
                p = new Posto(c.id(), c.body(), c.x(), c.z(), c.facing());
                posti.put(c.id(), p);
            }
            // l'andatura puo' essere cambiata (allenamento, misura arrivata): si usa sempre quella
            // che l'entita' sta usando adesso. Rimontare le fa perdere la posa, ed e' il prezzo.
            if (p.gait != c.gait()) {
                p.gait = c.gait();
                smonta(p);
            }
            p.stato = c.stato();
            p.sterzo = Math.max(-1.0, Math.min(1.0, c.sterzo()));
            if (p.doll == null) {
                monta(p);
            }
            boolean spenti = p.stato == Stato.DORME;
            if (p.doll != null && spenti != p.motoriSpenti) {
                try {
                    p.doll.motoriSpenti(spenti);
                    p.motoriSpenti = spenti;
                } catch (Throwable ignored) {
                    // un corpo che non accetta il cambio di motori resta come e': non e' fatale
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

    /** Toglie un'entita' dal mondo fisico (morte, uscita dalla sessione). */
    public static synchronized void esce(long id) {
        Posto p = posti.remove(id);
        smonta(p);
        istantanee.remove(id);
    }

    /**
     * Apre il mondo fisico, o lo riapre piu' grande se serve.
     *
     * CRESCE E NON RIMPICCIOLISCE, a scatti di 64 entita': Jolt prealloca i corpi, quindi cambiare
     * dimensione vuol dire ricreare il mondo e rimontare tutte le ragdoll - cioe' perdere le pose.
     * A scatti grossi succede qualche volta mentre la popolazione cresce e poi mai piu'.
     */
    private static boolean apriMondo(int quante) {
        if (mondo != null && capienzaMondo >= quante) {
            return true;
        }
        int nuova = ((quante + 63) / 64) * 64;
        try {
            if (mondo != null) {
                svuota();
                mondo.close();
            }
            PhysicsNatives.require();
            // le parti delle ragdoll, piu' il terreno e i tronchi, piu' un margine
            int parti = PhysicsWorld.MAX_PARTS_PER_BODY * nuova + 512;
            mondo = new PhysicsWorld(parti, 0);
            capienzaMondo = nuova;
            versioneTerreno = -1;        // il mondo e' nuovo: il terreno va rimesso
            alberiNelMondo = -1;
            corpoTerreno = -1;
            corpiAlberi.clear();
            System.out.println("[fisica] mondo unico per " + nuova + " entita'");
            return true;
        } catch (Throwable t) {
            System.out.println("[fisica] non disponibile (" + t + "): le entita' si muovono "
                    + "col passo geometrico.");
            fisicaRotta = true;
            mondo = null;
            return false;
        }
    }

    /**
     * IL TERRENO E GLI ALBERI, rifatti quando il mondo cambia.
     *
     * Il terreno si rifa' quando cambia la sua versione (una generazione nuova, un caricamento).
     * I tronchi si rifanno quando cambia il NUMERO di alberi: crescono anche in altezza, e
     * seguirne la crescita a ogni tick vorrebbe dire ricostruire ottanta corpi statici per niente -
     * un corpo urta la parte bassa del tronco, e quella non cambia.
     */
    private static void terrenoAggiornato() {
        int versione = src.world.getTerrainVersion();
        List<src.Flora.Tree> alberi = src.Flora.snapshotTrees();
        if (versione == versioneTerreno && alberi.size() == alberiNelMondo) {
            return;
        }
        int dim = src.world.getDim();
        if (versione != versioneTerreno) {
            if (corpoTerreno >= 0) {
                rimuovi(corpoTerreno);
                corpoTerreno = -1;
            }
            int lato = PhysicsWorld.latoCampioni(dim);
            float[] altezze = new float[lato * lato];
            for (int ix = 0; ix < lato; ix++) {
                for (int iz = 0; iz < lato; iz++) {
                    // oltre il bordo della mappa si ripete il bordo: meglio che un corpo che cade
                    // nel vuoto all'ultima cella
                    int cx = Math.min(ix, dim - 1);
                    int cz = Math.min(iz, dim - 1);
                    altezze[PhysicsWorld.indiceCampione(ix, iz, lato)] = src.world.surfaceY(cx, cz);
                }
            }
            corpoTerreno = mondo.addHeightField(altezze, lato, 1f, ATTRITO);
            versioneTerreno = versione;
            System.out.println("[fisica] terreno nella fisica: " + lato + "x" + lato
                    + " nodi per " + dim + " celle");
        }
        for (int id : corpiAlberi) {
            rimuovi(id);
        }
        corpiAlberi.clear();
        for (src.Flora.Tree t : alberi) {
            double h = Math.max(0.05, t.height());
            // il tronco sta al centro della sua cella, col piede alla quota del suo piede
            int id = mondo.addStaticCylinder(t.x + 0.5, t.baseY + h / 2, t.z + 0.5,
                    (float) t.trunkRadius(), (float) (h / 2), ATTRITO);
            corpiAlberi.add(id);
        }
        alberiNelMondo = alberi.size();
        mondo.optimize();
    }

    private static void rimuovi(int bodyId) {
        try {
            mondo.bodies().removeBody(bodyId);
            mondo.bodies().destroyBody(bodyId);
        } catch (Throwable ignored) {
            // il mondo sta chiudendo o il corpo non c'e' piu': non c'e' niente da salvare
        }
    }

    private static void monta(Posto p) {
        try {
            int cx = cella(p.x);
            int cz = cella(p.z);
            double quota = src.world.surfaceY(cx, cz);
            /*
             * UN GRUPPO DI COLLISIONE PER ENTITA'. Dentro un corpo le parti non si urtano (il
             * filtro di PhysicsWorld spegne le coppie dello stesso gruppo); fra entita' diverse si
             * urtano, ed E' QUELLO CHE SERVE: nel palco gli urti fra entita' erano un problema da
             * evitare con le corsie, qui sono il mondo. Due entita' che si incontrano si
             * incontrano.
             */
            p.doll = JoltRagdoll.spawn(mondo, p.body, p.x, quota, p.z, p.facing,
                    (int) (p.id & 0x7fffffff));
            p.angoli = p.gait == null ? null : p.gait.anglesAt(p.body, p.fase, null);
            if (p.angoli != null) {
                p.doll.setTargetAngles(p.angoli);
            }
            p.altezzaIniziale = p.doll.rootPosition().y() - quota;
            p.ultimoCom = p.doll.centerOfMass();
            p.sensi = new MotorSense(p.body);
            p.tickATerra = -1;
        } catch (Throwable t) {
            // un corpo che la fisica non regge non deve fermare il mondo: ne resta fuori, e si
            // conta, perche' una fisica che fallisce in silenzio sembra una fisica che funziona
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

    private static int cella(double metri) {
        int dim = src.world.getDim();
        return Math.max(0, Math.min(dim - 1, (int) Math.floor(metri)));
    }

    // ------------------------------------------------------------------ il tick

    /**
     * Avanza la fisica di tutti i corpi per questo tick di mondo.
     *
     * L'ordine dentro un passo: lo sterzo e la fase vanno ai motori, l'acqua spinge chi e'
     * immerso, poi il mondo integra. Alla fine del tick si leggono le posizioni e si lascia
     * l'istantanea per il disegno.
     */
    public static synchronized void avanza(double secondi) {
        if (mondo == null || fisicaRotta || posti.isEmpty()) {
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
            double quotaAcqua = src.world.getWaterLevel();
            for (int i = 0; i < passi; i++) {
                for (Posto p : posti.values()) {
                    if (p.doll == null || p.gait == null) {
                        continue;
                    }
                    /*
                     * LA FASE SCORRE SOLO SE L'ENTITA' HA SCELTO DI MUOVERSI. Ferma, i motori
                     * tengono la posa che avevano: il corpo prova a stare su senza camminare, che
                     * e' quello che "riposare" vuol dire dentro la gravita'. Senza questo le gambe
                     * si muoverebbero sempre e per tutti, anche per chi sta mangiando, e lo
                     * spostamento non sarebbe piu' la conseguenza di una decisione.
                     */
                    if (p.stato == Stato.CAMMINA) {
                        p.fase += PhysicsWorld.TIME_STEP;
                    }
                    if (p.sensi != null && i % ogniQuanti == 0) {
                        p.sensi.read(p.doll, 0, 2 * Math.PI * p.gait.frequency() * p.fase);
                    }
                    if (p.stato != Stato.DORME) {
                        // lo sterzo vale solo per chi si muove: un corpo fermo che tiene i giunti
                        // spostati da un lato non sta girando, si sta solo sbilanciando
                        double u = p.stato == Stato.CAMMINA ? p.sterzo : 0.0;
                        p.angoli = p.gait.targetAngles(p.body, p.fase, p.sensi, u, p.angoli);
                        p.doll.setTargetAngles(p.angoli);
                    }
                    if (i % GALLEGGIO_OGNI == 0) {
                        acqua(p, quotaAcqua);
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
            }
        } catch (Throwable t) {
            System.out.println("[fisica] la fisica si e' fermata (" + t + "): spenta.");
            fisicaRotta = true;
            svuota();
            return;
        }
        if (corpi > 0) {
            double ms = (System.nanoTime() - t0) / 1e6 / corpi;
            // media esponenziale: un tick lento perche' la macchina era occupata non deve
            // far sembrare la fisica il doppio piu' cara di quanto e'
            costoPerCorpoMs = 0.9 * costoPerCorpoMs + 0.1 * ms;
        }
    }

    /**
     * L'ACQUA SU UN CORPO: spinta e resistenza su ogni pezzo che sta sotto il pelo, in una
     * colonna allagata.
     *
     * Si guarda COLONNA PER COLONNA e pezzo per pezzo, perche' l'acqua di questo mondo non e' un
     * mare: sono le depressioni allagate (world.isWaterColumn), e un corpo lungo puo' avere le
     * gambe in acqua e il tronco fuori. Un corpo con la densita' dell'acqua - e sono tutti, vedi
     * Automaton.DENSITY - non galleggia e non affonda: resta dove e'. Quello che cambia e' che
     * dentro l'acqua si fa molta piu' fatica a muoversi, e questo lo decide Jolt.
     */
    private static void acqua(Posto p, double quotaAcqua) {
        int n = p.doll.clusterCount();
        for (int c = 0; c < n; c++) {
            V3 pos = p.doll.clusterPosition(c);
            if (pos.y() > quotaAcqua) {
                continue;
            }
            if (!src.world.isWaterColumn(cella(pos.x()), cella(pos.z()))) {
                continue;
            }
            mondo.galleggia(p.doll.clusterBody(c), quotaAcqua,
                    (float) (PhysicsWorld.TIME_STEP * GALLEGGIO_OGNI));
        }
    }

    /** Legge dove il corpo e' arrivato e lascia l'istantanea per il disegno. */
    private static void leggi(Posto p) {
        if (p.doll == null) {
            istantanee.remove(p.id);
            return;
        }
        V3 com = p.doll.centerOfMass();
        if (p.ultimoCom != null) {
            double d = Math.hypot(com.x() - p.ultimoCom.x(), com.z() - p.ultimoCom.z());
            if (Double.isFinite(d)) {
                metriTotali += d;
            }
        }
        p.ultimoCom = com;

        int cx = cella(com.x());
        int cz = cella(com.z());
        double quotaTerreno = src.world.surfaceY(cx, cz);
        double alto = p.doll.rootPosition().y() - quotaTerreno;
        boolean abbassato = p.altezzaIniziale > 1e-6 && alto / p.altezzaIniziale < GaitEvaluator.FALL_RATIO;
        /*
         * COLLASSO = ABBASSATO E FERMO, e si CONTA senza intervenire. Non "abbassato": chi
         * striscia sta abbassato per tutta la vita e si sta muovendo. E nessuno viene rimesso in
         * piedi - chi cade resta caduto, e se non riesce ad arrivare al cibo muore (voce 24).
         */
        if (abbassato) {
            if (p.tickATerra < 0) {
                collassi++;
                p.tickATerra = 0;
            } else {
                p.tickATerra++;
            }
        } else {
            p.tickATerra = -1;
        }

        try {
            int n = p.doll.clusterCount();
            V3[] pos = new V3[n];
            Rot[] rot = new Rot[n];
            p.doll.leggiTrasformate(pos, rot);
            V3 perno = new V3(com.x(), quotaTerreno, com.z());
            Posizione dove = new Posizione(com.x(), com.z(), p.doll.yawRoot(),
                    p.doll.rootPosition().y(), abbassato);
            istantanee.put(p.id, new Istantanea(p.doll, pos, rot, perno, dove));
        } catch (Throwable ignored) {
            // se non si riesce a leggere le trasformate, il disegno usa l'istantanea di prima
        }
    }

    // ------------------------------------------------------------------ cosa legge la simulazione

    /**
     * DOVE IL CORPO E' ARRIVATO. E' questa la riga che rende il mondo unico: la simulazione non
     * sposta piu' l'entita', la LEGGE dal suo corpo.
     *
     * @return null se questa entita' non e' nel mondo fisico (e allora chi chiama si muove come
     *         prima, col passo geometrico)
     */
    public static Posizione posizioneDi(long id) {
        Istantanea i = istantanee.get(id);
        return i == null ? null : i.dove();
    }

    /**
     * LA POSA DISEGNATA E' LA POSA FISICA. Nessuna ricostruzione, nessuna rotazione imposta: nel
     * mondo unico anche l'imbardata viene dal corpo, quindi non si gira piu' niente - si sottrae
     * soltanto il perno, perche' il motore grafico lo somma di nuovo (vedi {@link Istantanea}).
     *
     * @return null se questa entita' non e' nel mondo fisico
     */
    public static BodyPose posaDi(long id) {
        Istantanea i = istantanee.get(id);
        if (i == null) {
            return null;
        }
        BodyPose posa = i.corpo().posaDa(i.posizioni(), i.rotazioni());
        return posa.transformed(Rot.IDENTITY, i.perno());
    }

    public static synchronized boolean contiene(long id) {
        Posto p = posti.get(id);
        return p != null && p.doll != null;
    }

    /**
     * Gli angoli VERI dei giunti, DENTRO I LIMITI. Serve alla diagnostica e alle prove: il disegno
     * usa {@link #posaDi}, che non passa per gli angoli.
     *
     * Il taglio sui limiti c'e' perche' per i giunti a due e tre assi l'angolo misurato e' una
     * PROIEZIONE della rotazione relativa e non una decomposizione esatta (in Jolt quei giunti
     * diventano un solo vincolo a sei gradi con gli assi ortogonalizzati), e puo' dare un valore
     * fuori dalla corsa: misurato fino a 1.8 rad, sempre su giunti a 3 gradi, mai su una cerniera.
     */
    public static synchronized float[] angoliDi(long id, float[] out) {
        Posto p = posti.get(id);
        if (p == null || p.doll == null) {
            return null;
        }
        float[] q = p.doll.jointAngles(out);
        List<Dof> dofs = p.body.dofs();
        for (int i = 0; i < q.length && i < dofs.size(); i++) {
            Dof d = dofs.get(i);
            q[i] = (float) Math.max(d.lower, Math.min(d.upper, q[i]));
        }
        return q;
    }

    public static synchronized String stats() {
        return String.format(Locale.ROOT,
                "fisica: %d entita' nel mondo (mondo per %d, %.2f ms per corpo), %d passi, "
                        + "%.1f m percorsi, %d collassi%s%s",
                posti.size(), capienzaMondo, costoPerCorpoMs, passiFisica, metriTotali, collassi,
                montaggiFalliti > 0 ? ", " + montaggiFalliti + " corpi che la fisica non regge" : "",
                fisicaRotta ? " (fisica spenta da un errore)" : "");
    }

    /** Chiude il mondo fisico (fine sessione). */
    public static synchronized void chiudi() {
        svuota();
        if (mondo != null) {
            mondo.close();
            mondo = null;
        }
        capienzaMondo = 0;
        versioneTerreno = -1;
        alberiNelMondo = -1;
        corpoTerreno = -1;
        corpiAlberi.clear();
        fisicaRotta = false;
        passiFisica = 0;
        collassi = 0;
        montaggiFalliti = 0;
        metriTotali = 0;
    }
}
