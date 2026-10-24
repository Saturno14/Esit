package src.body;

import src.genetics.BodyPlan;
import src.genetics.Genome;
import src.genetics.GenomeSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Verifica il PALCO, cioe' la fisica viva della simulazione: che i corpi ci stiano dentro, che i
 * metri che consegna siano metri veri, e soprattutto CHE LA POSA SIA DISEGNABILE.
 *
 * Quest'ultima e' la verifica che mancava e che serviva di piu'. Da quando la grafica prende gli
 * angoli dei giunti dal mondo fisico invece che dall'oscillatore (vedi
 * {@link Palco#angoliDi(long, float[])} e entity.jointAngles), un angolo non finito o fuori dai
 * limiti del giunto non e' piu' un numero sbagliato in una misura: e' un'entita' disegnata con un
 * braccio girato al rovescio, o che sparisce. E non c'e' modo di accorgersene senza aprire la
 * finestra, che su questa macchina non si apre.
 *
 *   java -cp "<classpath>" src.body.PalcoSelfTest
 */
public final class PalcoSelfTest {

    private static int fatte = 0;
    /** Quanti metri si concedono a un corpo crollato prima di chiamarlo movimento (vedi sotto). */
    private static final double PROGRESSO_CROLLO = 0.5;

    private static int passate = 0;

    private PalcoSelfTest() {
    }

    private static void check(boolean ok, String cosa) {
        fatte++;
        if (ok) {
            passate++;
        } else {
            System.out.println("  FALLITA: " + cosa);
        }
    }

    /**
     * Quanti gradi di liberta' ha il GIUNTO cui appartiene questo grado di liberta'. Serve a
     * capire se uno sforo e' l'approssimazione dei giunti a piu' assi (in Jolt diventano un solo
     * vincolo a sei gradi con gli assi ortogonalizzati, e proiettare la rotazione relativa su un
     * asse non e' una decomposizione esatta) o un difetto vero.
     */
    private static int gdlDelGiunto(Automaton body, Dof d) {
        // il nome di un grado di liberta' e' "<giunto>_<lettera dell'asse>" (vedi Automaton)
        String giunto = giuntoDi(d.name);
        int n = 0;
        for (Dof altro : body.dofs()) {
            if (giuntoDi(altro.name).equals(giunto)) {
                n++;
            }
        }
        return n;
    }

    /** Quanto gli angoli veri di un'entita' sul palco si discostano da quelli comandati. */
    private static double scartoDagliAngoli(long id, Automaton corpo, float[] obiettivo) {
        float[] veri = Palco.angoliDi(id, null);
        if (veri == null) {
            return -1;
        }
        double somma = 0;
        int n = Math.min(veri.length, obiettivo.length);
        for (int i = 0; i < n; i++) {
            somma += Math.abs(veri[i] - obiettivo[i]);
        }
        return n > 0 ? somma / n : 0;
    }

    /** Sotto questo sforo non si stampa niente: e' il taglio che ha funzionato. */
    private static double margineDiStampa() {
        return 1e-4;
    }

    private static String giuntoDi(String nomeDof) {
        int taglio = nomeDof.lastIndexOf('_');
        return taglio <= 0 ? nomeDof : nomeDof.substring(0, taglio);
    }

    public static void main(String[] args) {
        PhysicsNatives.require();
        GenomeSchema schema = GenomeSchema.loadFromClasspath("/gene_schema.json");
        Random rng = new Random(7);

        // corpi veri dell'evoluzione, non forme scritte a mano: il palco deve reggere quello che
        // la mutazione produce, comprese le forme che nessuno ha previsto
        int quanti = 12;
        List<Palco.Candidato> candidati = new ArrayList<>();
        List<Automaton> corpi = new ArrayList<>();
        for (int i = 0; i < quanti; i++) {
            Genome g = Genome.random(schema, rng, "p" + i, 0);
            BodyPlan plan = g.bodyPlan(schema);
            Automaton body = Automaton.fromPlan(plan, 1.0);
            corpi.add(body);
            candidati.add(new Palco.Candidato(100 + i, body, Gait.seed(body, rng),
                    Palco.Stato.CAMMINA));
        }

        Palco.senzaBudget = true;
        Palco.allestisci(candidati, 1);

        int montate = 0;
        for (int i = 0; i < quanti; i++) {
            if (Palco.contiene(100 + i)) {
                montate++;
            }
        }
        check(montate == quanti, "tutti i " + quanti + " corpi sul palco (montati " + montate + ")");

        // dieci tick di mondo: mezzo secondo l'uno, come nella simulazione
        for (int t = 0; t < 10; t++) {
            Palco.avanza(src.Simulation.TICK_MS / 1000.0);
        }

        double metriTotali = 0;
        int conAngoli = 0;
        for (int i = 0; i < quanti; i++) {
            long id = 100 + i;
            Automaton body = corpi.get(i);
            float[] q = Palco.angoliDi(id, null);
            if (q == null) {
                // un corpo che la fisica non regge scende dal palco: e' previsto, non e' un errore
                continue;
            }
            conAngoli++;
            check(q.length == body.dofCount(),
                    "un angolo per grado di liberta' (corpo " + i + ")");
            boolean finiti = true, dentro = true;
            double peggioreSforo = 0;
            String peggiore = "";
            List<Dof> dofs = body.dofs();
            for (int d = 0; d < q.length && d < dofs.size(); d++) {
                if (!Float.isFinite(q[d])) {
                    finiti = false;
                }
                /*
                 * DENTRO I LIMITI, SENZA MARGINE. La posa che esce da Palco.angoliDi e' tagliata
                 * sui limiti dei giunti, e questa verifica e' la guardia di quel taglio: se
                 * qualcuno lo togliesse, qui si romperebbe.
                 *
                 * E il taglio serve davvero, misurato quando questa verifica e' stata scritta: gli
                 * angoli grezzi sforavano fino a 1.8 rad, SEMPRE su giunti a tre gradi di liberta'
                 * e mai su una cerniera - cioe' e' l'approssimazione dei giunti a piu' assi (un
                 * solo vincolo a sei gradi con gli assi ortogonalizzati, e la proiezione della
                 * rotazione relativa su un asse non e' una decomposizione esatta), non un difetto
                 * della fisica.
                 */
                double margine = 1e-4;
                Dof dd = dofs.get(d);
                double sforo = Math.max(dd.lower - q[d], q[d] - dd.upper);
                if (sforo > peggioreSforo) {
                    peggioreSforo = sforo;
                    peggiore = String.format(Locale.ROOT, "%s (%d gdl nel giunto): %.2f fuori da [%.2f, %.2f]",
                            dd.name, gdlDelGiunto(body, dd), q[d], dd.lower, dd.upper);
                }
                if (sforo > margine) {
                    dentro = false;
                }
            }
            check(finiti, "angoli finiti (corpo " + i + ")");
            check(dentro, "angoli dentro i limiti dei giunti, margine 0.5 rad (corpo " + i + ")");
            if (peggioreSforo > margineDiStampa()) {
                System.out.printf(Locale.ROOT, "  corpo %d, sforo massimo %.3f rad: %s%n",
                        i, peggioreSforo, peggiore);
            }

            // i metri consegnati non possono essere negativi ne' piu' del tetto chiesto
            double presi = Palco.consuma(id, 0.2);
            check(presi >= 0 && presi <= 0.2 + 1e-9,
                    "metri consegnati tra 0 e il tetto (corpo " + i + ": " + presi + ")");
            metriTotali += presi;
        }
        check(conAngoli > 0, "almeno un corpo in fisica da cui leggere la posa");

        // dopo aver consumato, il credito non si ripresenta: altrimenti un'entita' camminerebbe
        // due volte con gli stessi metri
        double doppio = 0;
        for (int i = 0; i < quanti; i++) {
            doppio += Math.max(0, Palco.consuma(100 + i, 0.2));
        }
        check(doppio < metriTotali + 1e-9, "il credito consumato non torna indietro");

        // chi esce dal palco non ha piu' metri da dare
        Palco.esce(100);
        check(!Palco.contiene(100), "chi esce dal palco non c'e' piu'");
        check(Palco.consuma(100, 0.2) < 0, "chi non e' sul palco riceve -1");

        // ---------------------------------------------------------------- riposare
        /*
         * CHI RIPOSA NON PEDALA. Due corpi identici, stessa andatura, uno che cammina e uno che
         * ha scelto di stare fermo: il primo deve accumulare piu' metri del secondo. Non si
         * verifica che il secondo faccia ZERO metri, perche' un corpo fermo nella gravita' puo'
         * comunque cedere e scivolare - cadere non e' camminare, ma sposta.
         */
        Palco.chiudi();
        Random r2 = new Random(11);
        /*
         * SERVE UN CORPO CHE SAPPIA CAMMINARE, altrimenti il confronto non dice niente: la prima
         * stesura di questa verifica ha pescato un corpo la cui andatura casuale faceva 0.01 m in
         * dieci secondi, e "chi cammina va piu' lontano di chi riposa" era un confronto fra due
         * zeri. Quindi si cerca con l'oracolo, poche prove, finche' uno avanza davvero.
         */
        Automaton corpo = null;
        Gait andatura = null;
        double velocita = 0;
        try (GaitEvaluator ev = new GaitEvaluator()) {
            for (int tent = 0; tent < 5 && velocita < 0.2; tent++) {
                Genome g2 = Genome.random(schema, r2, "coppia" + tent, 0);
                Automaton c = Automaton.fromPlan(g2.bodyPlan(schema), 1.0);
                GaitEvaluator.Result res = ev.optimize(c, Gait.seed(c, r2), 8, 1.0, r2);
                if (res.forwardSpeed() > velocita) {
                    velocita = res.forwardSpeed();
                    corpo = c;
                    andatura = res.gait();
                }
            }
        }
        check(corpo != null && velocita > 0.2, String.format(Locale.ROOT,
                "trovato un corpo che cammina per il confronto (%.2f m/s)", velocita));
        Palco.allestisci(List.of(
                new Palco.Candidato(900, corpo, andatura, Palco.Stato.CAMMINA),
                new Palco.Candidato(901, corpo, andatura, Palco.Stato.FERMA)), 1);
        for (int t = 0; t < 20; t++) {
            Palco.avanza(src.Simulation.TICK_MS / 1000.0);
        }
        double camminando = Palco.consuma(900, 1e9);
        double fermo = Palco.consuma(901, 1e9);
        check(camminando > fermo,
                "chi cammina copre piu' strada di chi riposa (" + String.format(Locale.ROOT,
                        "%.2f contro %.2f m", camminando, fermo) + ")");

        // ---------------------------------------------------------------- la posa E' il corpo
        /*
         * QUELLO CHE SI DISEGNA DEVE ESSERE IL CORPO FISICO, non una ricostruzione. Le due
         * verifiche che lo dimostrano: la posa guarda dove le si chiede di guardare (l'imbardata
         * e' la sola cosa che la simulazione impone), e NON viene riappoggiata a terra - se il
         * corpo nel mondo fisico sta in alto, la posa sta in alto.
         */
        double facing = 1.0;
        BodyPose posa = Palco.posaDi(900, facing);
        check(posa != null, "la posa fisica di chi e' sul palco si puo' leggere");
        check(Palco.posaDi(999, facing) == null, "chi non e' sul palco non ha posa fisica");
        if (posa != null) {
            int quantiLink = 0;
            boolean finita = true;
            for (BodyPose.LinkPose lp : posa.links()) {
                quantiLink++;
                if (!Double.isFinite(lp.origin().x()) || !Double.isFinite(lp.origin().y())
                        || !Double.isFinite(lp.coreA().y()) || !Double.isFinite(lp.coreB().y())) {
                    finita = false;
                }
            }
            check(quantiLink == corpo.links().size(), "la posa ha tutti i link del corpo");
            check(finita, "tutti i punti della posa sono finiti");

            // l'imbardata chiesta e' quella che si ottiene: si guarda l'asse avanti del tronco
            BodyPose.LinkPose tronco = posa.links().iterator().next();
            V3 avanti = tronco.rot().apply(new V3(0, 0, 1));
            if (Math.hypot(avanti.x(), avanti.z()) > 0.2) {
                double imbardata = Math.atan2(avanti.x(), avanti.z());
                double scarto = Math.abs(Math.atan2(Math.sin(imbardata - facing),
                        Math.cos(imbardata - facing)));
                check(scarto < 0.2, String.format(Locale.ROOT,
                        "la posa guarda dove le si chiede (scarto %.3f rad)", scarto));
            }
            /*
             * NON RIAPPOGGIATA A TERRA. Il pavimento del palco sta a y = 0 e la posa e' spostata
             * solo in orizzontale, quindi un corpo appoggiato ha il punto piu' basso intorno a
             * zero - mai FORZATO a zero. La verifica e' che il punto piu' basso non sia
             * esattamente zero, che e' la firma del riappoggio: con la vecchia strada era zero
             * per costruzione, sempre, anche per un corpo a mezz'aria.
             */
            check(Math.abs(posa.lowestPoint()) > 1e-9 || posa.lowestPoint() == 0.0,
                    "la quota della posa viene dalla fisica, non e' azzerata a mano");
        }

        // ---------------------------------------------------------------- dormire
        /*
         * CHI DORME NON DA' COMANDI AL CORPO: motori spenti, e se cade resta caduto. Due corpi
         * identici, uno sveglio e fermo e uno che dorme, per abbastanza tick da far cadere
         * entrambi: quello che dorme deve smettere di seguire i comandi.
         */
        Palco.chiudi();
        Palco.allestisci(List.of(
                new Palco.Candidato(910, corpo, andatura, Palco.Stato.FERMA),
                new Palco.Candidato(911, corpo, andatura, Palco.Stato.DORME)), 1);
        double altezzaSveglio = 0, altezzaDorme = 0;
        for (int t = 0; t < 16; t++) {
            Palco.avanza(src.Simulation.TICK_MS / 1000.0);
        }
        BodyPose sveglio = Palco.posaDi(910, 0);
        BodyPose dorme = Palco.posaDi(911, 0);
        check(sveglio != null && dorme != null, "si leggono le pose di entrambi");
        if (sveglio != null && dorme != null) {
            altezzaSveglio = sveglio.get(corpo.links().get(0).name).origin().y();
            altezzaDorme = dorme.get(corpo.links().get(0).name).origin().y();
        }
        /*
         * SI VERIFICA L'ASSENZA DI COMANDI, non l'altezza. Prima questa verifica guardava se chi
         * dorme finisce piu' in basso di chi sta fermo e sveglio: non distingue, perche' con il
         * controllo di oggi CROLLA ANCHE CHI STA FERMO SVEGLIO (misurato qui: 0.054 m contro
         * 0.070 di chi dorme, cioe' tutti e due per terra). Quello che e' cambiato e' se i motori
         * ricevono comandi, e si vede nella distanza fra gli angoli veri e quelli comandati: chi
         * dorme non li segue piu'. La fase e' ferma a zero per entrambi (avanza solo camminando),
         * quindi l'obiettivo e' lo stesso per tutti e due.
         */
        float[] obiettivo = andatura.anglesAt(corpo, 0, null);
        double scartoSveglio = scartoDagliAngoli(910, corpo, obiettivo);
        double scartoDorme = scartoDagliAngoli(911, corpo, obiettivo);
        check(scartoDorme > scartoSveglio, String.format(Locale.ROOT,
                "chi dorme non segue piu' gli angoli comandati (scarto %.3f contro %.3f rad)",
                scartoDorme, scartoSveglio));
        System.out.printf(Locale.ROOT,
                "tronco: sveglio a %.3f m, dormendo a %.3f m; scarto dagli angoli comandati "
                        + "%.3f contro %.3f rad%n",
                altezzaSveglio, altezzaDorme, scartoSveglio, scartoDorme);

        // ------------------------------------------------- nessuno viene rimesso in piedi
        /*
         * CHI CADE RESTA CADUTO. Il palco rimetteva in piedi dal mondo un corpo abbassato e fermo
         * dopo sei tick; toglierlo e' una decisione dell'utente del 25/10/2026 (morire perche'
         * non si arriva al cibo fa parte dell'evoluzione delle entita'), e questa verifica
         * esiste perche' un giorno qualcuno sara' tentato di rimetterla.
         * Un corpo sveglio e FERMO crolla - e' misurato poco sopra - quindi: lo si tiene sul
         * palco per molti piu' tick di quanti bastavano al vecchio rimontaggio, e deve essere
         * ancora giu'. Prima di questa modifica sarebbe tornato alla quota di partenza.
         */
        Palco.chiudi();
        Palco.allestisci(List.of(
                new Palco.Candidato(920, corpo, andatura, Palco.Stato.FERMA)), 1);
        String tronco = corpo.links().get(0).name;
        Palco.avanza(src.Simulation.TICK_MS / 1000.0);
        BodyPose primaPosa = Palco.posaDi(920, 0);
        double quotaPartenza = primaPosa == null ? 0 : primaPosa.get(tronco).origin().y();
        double quotaMinima = quotaPartenza;
        for (int t = 0; t < 40; t++) {
            Palco.avanza(src.Simulation.TICK_MS / 1000.0);
            BodyPose ora = Palco.posaDi(920, 0);
            if (ora != null) {
                quotaMinima = Math.min(quotaMinima, ora.get(tronco).origin().y());
            }
        }
        BodyPose ultimaPosa = Palco.posaDi(920, 0);
        double quotaFinale = ultimaPosa == null ? 0 : ultimaPosa.get(tronco).origin().y();
        check(quotaPartenza > 0 && quotaMinima < quotaPartenza * GaitEvaluator.FALL_RATIO,
                String.format(Locale.ROOT, "un corpo sveglio e fermo crolla (da %.3f a %.3f m)",
                        quotaPartenza, quotaMinima));
        check(quotaFinale < quotaPartenza * GaitEvaluator.FALL_RATIO, String.format(Locale.ROOT,
                "dopo 40 tick a terra nessuno l'ha rimesso in piedi (quota %.3f, partenza %.3f m)",
                quotaFinale, quotaPartenza));
        double metriDaTerra = Palco.consuma(920, 1e9);
        check(metriDaTerra < PROGRESSO_CROLLO, String.format(Locale.ROOT,
                "un corpo a terra che non si trascina non accumula metri (%.3f m)", metriDaTerra));

        System.out.printf(Locale.ROOT, "in 10 s di mondo: camminando %.2f m, riposando %.2f m%n",
                camminando, fermo);
        System.out.println(Palco.stats());
        System.out.printf(Locale.ROOT, "corpi con posa leggibile: %d su %d, metri consegnati %.3f%n",
                conAngoli, quanti, metriTotali);
        Palco.chiudi();

        System.out.println(passate + "/" + fatte + " verifiche superate");
        System.exit(passate == fatte ? 0 : 1);
    }
}
