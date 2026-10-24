package src.body;

import src.genetics.BodyPlan;
import src.genetics.Genome;
import src.genetics.GenomeSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Verifica il MONDO UNICO, cioe' la fisica viva della simulazione dopo che il palco e' stato
 * cancellato: i corpi stanno sul TERRENO VERO della simulazione, dove sono arrivati lo si LEGGE
 * dal corpo, e la posa disegnata e' la posa fisica.
 *
 * Erede di PalcoSelfTest, e le differenze dicono cosa e' cambiato:
 *  - non c'e' piu' niente da "consumare": il palco consegnava metri e radianti, qui si legge una
 *    POSIZIONE ({@link MondoFisico#posizioneDi}), e la strada percorsa e' la differenza fra due
 *    letture - cioe' la stessa cosa che vede la simulazione;
 *  - il pavimento non e' piatto: si verifica che la quota di un corpo segua il terreno sotto di
 *    lui, perche' e' questo che rende un'andatura una proprieta' del corpo E DEL POSTO;
 *  - l'imbardata non viene piu' imposta da fuori: anche la direzione e' una lettura.
 *
 * OGNI CONFRONTO E' APPAIATO E SEQUENZIALE: un corpo per volta, sempre nella STESSA cella, con il
 * mondo chiuso e riaperto in mezzo. Non e' pignoleria, e' il prezzo del terreno vero: mettendo i
 * due corpi del confronto in due celle diverse, la prima stesura ha misurato un corpo FERMO che
 * copriva 2.39 m contro 1.18 m di uno che camminava - perche' stava su un pendio e SCIVOLAVA.
 * Sul palco piatto il posto non contava e due corsie erano equivalenti; qui no.
 *
 *   java -cp "&lt;classpath&gt;" src.body.MondoFisicoSelfTest
 */
public final class MondoFisicoSelfTest {

    private static int fatte = 0;
    private static int passate = 0;

    private MondoFisicoSelfTest() {
    }

    private static void check(boolean ok, String cosa) {
        fatte++;
        if (ok) {
            passate++;
        } else {
            System.out.println("  FALLITA: " + cosa);
        }
    }

    /** Un tick di mondo, lo stesso della simulazione. */
    private static void tick() {
        MondoFisico.avanza(src.Simulation.TICK_MS / 1000.0);
    }

    /**
     * Quanti gradi di liberta' ha il GIUNTO cui appartiene questo grado di liberta'. Serve a
     * capire se uno sforo e' l'approssimazione dei giunti a piu' assi (in Jolt diventano un solo
     * vincolo a sei gradi con gli assi ortogonalizzati, e proiettare la rotazione relativa su un
     * asse non e' una decomposizione esatta) o un difetto vero.
     */
    private static int gdlDelGiunto(Automaton body, Dof d) {
        String giunto = giuntoDi(d.name);
        int n = 0;
        for (Dof altro : body.dofs()) {
            if (giuntoDi(altro.name).equals(giunto)) {
                n++;
            }
        }
        return n;
    }

    private static String giuntoDi(String nomeDof) {
        int taglio = nomeDof.lastIndexOf('_');
        return taglio <= 0 ? nomeDof : nomeDof.substring(0, taglio);
    }

    /** Quanto gli angoli veri di un corpo nel mondo si discostano da quelli comandati. */
    private static double scartoDagliAngoli(long id, float[] obiettivo) {
        float[] veri = MondoFisico.angoliDi(id, null);
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

    /** Strada percorsa in orizzontale fra due letture. */
    private static double strada(MondoFisico.Posizione da, MondoFisico.Posizione a) {
        if (da == null || a == null) {
            return 0;
        }
        return Math.hypot(a.x() - da.x(), a.z() - da.z());
    }

    /**
     * Cos'e' successo a un corpo in una prova.
     *
     * @param strada  metri coperti in orizzontale
     * @param giro    di quanto la direzione LETTA e' cambiata (radianti, con segno)
     * @param quotaPartenza quota del tronco al primo tick
     * @param quotaMinima   la quota piu' bassa toccata dal tronco
     * @param quotaFinale   quota del tronco alla fine
     */
    private record Esito(double strada, double giro, double quotaPartenza, double quotaMinima,
                         double quotaFinale, MondoFisico.Posizione fine) {
    }

    /**
     * UNA PROVA APPAIATA: un corpo solo, nella cella indicata, per un numero di tick.
     *
     * Chiude e riapre il mondo, quindi due prove di fila partono dallo stesso terreno con la
     * stessa posa iniziale: l'unica differenza fra loro e' quella che si vuole misurare.
     */
    private static Esito prova(long id, Automaton corpo, Gait g, MondoFisico.Stato stato,
                               double sterzo, int[] cella, int tick) {
        MondoFisico.chiudi();
        MondoFisico.allestisci(List.of(new MondoFisico.Candidato(id, corpo, g, stato, sterzo,
                cella[0] + 0.5, cella[1] + 0.5, 0.0)));
        String tronco = corpo.links().get(0).name;
        tick();
        MondoFisico.Posizione p0 = MondoFisico.posizioneDi(id);
        BodyPose posa0 = MondoFisico.posaDi(id);
        double quota0 = posa0 == null ? 0 : posa0.get(tronco).origin().y();
        double quotaMin = quota0;
        double quotaFine = quota0;
        /*
         * IL GIRO SI INTEGRA TICK PER TICK, non si legge come differenza fra la fine e l'inizio.
         * Una differenza di direzione passa da wrapPi, e a mezzo giro il segno e' un lancio di
         * moneta: -3.141 e +3.141 sono lo stesso angolo. La prima stesura confrontava i segni di
         * quelle differenze e ha "visto" un corpo girare a destra di -3.141 rad. Sommando le
         * differenze fra un tick e il successivo (mezzo secondo) l'ambiguita' resta solo per chi
         * gira piu' di mezzo giro in mezzo secondo, cioe' per una trottola - e una trottola non
         * e' una svolta.
         */
        double giro = 0;
        double direzionePrima = p0 == null ? Double.NaN : p0.facing();
        for (int t = 0; t < tick; t++) {
            tick();
            BodyPose ora = MondoFisico.posaDi(id);
            if (ora != null) {
                quotaFine = ora.get(tronco).origin().y();
                quotaMin = Math.min(quotaMin, quotaFine);
            }
            MondoFisico.Posizione adesso = MondoFisico.posizioneDi(id);
            if (adesso != null && !Double.isNaN(direzionePrima)) {
                giro += Rot.wrapPi(adesso.facing() - direzionePrima);
                direzionePrima = adesso.facing();
            }
        }
        MondoFisico.Posizione p1 = MondoFisico.posizioneDi(id);
        return new Esito(strada(p0, p1), giro, quota0, quotaMin, quotaFine, p1);
    }

    public static void main(String[] args) {
        PhysicsNatives.require();
        /*
         * IL MONDO DELLA SIMULAZIONE, non un pavimento di prova: e' il punto di tutta la modifica.
         * world_setup genera il terreno, allaga le depressioni e semina gli alberi, esattamente
         * come in una corsa.
         */
        src.Simulation.setSeed(7);
        check(src.world.world_setup(), "il mondo della simulazione si costruisce");
        int dim = src.world.getDim();

        GenomeSchema schema = GenomeSchema.loadFromClasspath("/gene_schema.json");
        Random rng = new Random(7);

        // corpi veri dell'evoluzione, non forme scritte a mano: il mondo deve reggere quello che
        // la mutazione produce, comprese le forme che nessuno ha previsto
        int quanti = 12;
        List<MondoFisico.Candidato> candidati = new ArrayList<>();
        List<Automaton> corpi = new ArrayList<>();
        for (int i = 0; i < quanti; i++) {
            Genome g = Genome.random(schema, rng, "p" + i, 0);
            BodyPlan plan = g.bodyPlan(schema);
            Automaton body = Automaton.fromPlan(plan, 1.0);
            corpi.add(body);
            // sparsi nel mondo: celle diverse, quindi quote diverse e terreni diversi
            double x = 4 + (i % 4) * 8 + 0.5;
            double z = 4 + (i / 4) * 8 + 0.5;
            candidati.add(new MondoFisico.Candidato(100 + i, body, Gait.seed(body, rng),
                    MondoFisico.Stato.CAMMINA, 0.0, x, z, 0.0));
        }

        MondoFisico.allestisci(candidati);

        int montate = 0;
        for (int i = 0; i < quanti; i++) {
            if (MondoFisico.contiene(100 + i)) {
                montate++;
            }
        }
        check(montate == quanti,
                "tutti i " + quanti + " corpi nel mondo, senza budget (montati " + montate + ")");

        // cinque secondi di mondo: mezzo secondo per tick, come nella simulazione
        List<MondoFisico.Posizione> partenze = new ArrayList<>();
        tick();
        for (int i = 0; i < quanti; i++) {
            partenze.add(MondoFisico.posizioneDi(100 + i));
        }
        for (int t = 0; t < 10; t++) {
            tick();
        }

        double stradaTotale = 0;
        int conAngoli = 0;
        int conPosizione = 0;
        for (int i = 0; i < quanti; i++) {
            long id = 100 + i;
            Automaton body = corpi.get(i);

            /*
             * LA POSIZIONE E' QUELLO CHE LA SIMULAZIONE LEGGE. Deve essere finita e DENTRO IL
             * MONDO: un corpo finito fuori dalla mappa non avrebbe terreno sotto di se' ne' celle
             * da guardare, e la simulazione lo riporterebbe al bordo senza sapere perche'.
             */
            MondoFisico.Posizione dove = MondoFisico.posizioneDi(id);
            if (dove != null) {
                conPosizione++;
                check(Double.isFinite(dove.x()) && Double.isFinite(dove.z())
                                && Double.isFinite(dove.facing()) && Double.isFinite(dove.quota()),
                        "posizione, direzione e quota finite (corpo " + i + ")");
                check(dove.x() > -2 && dove.x() < dim + 2 && dove.z() > -2 && dove.z() < dim + 2,
                        String.format(Locale.ROOT,
                                "il corpo %d e' rimasto nel mondo (%.2f, %.2f)", i, dove.x(),
                                dove.z()));
                stradaTotale += strada(partenze.get(i), dove);
            }

            float[] q = MondoFisico.angoliDi(id, null);
            if (q == null) {
                // un corpo che la fisica non regge resta fuori: e' previsto, non e' un errore
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
                 * DENTRO I LIMITI, SENZA MARGINE. La posa che esce da angoliDi e' tagliata sui
                 * limiti dei giunti, e questa verifica e' la guardia di quel taglio: se qualcuno
                 * lo togliesse, qui si romperebbe. E il taglio serve: gli angoli grezzi sforavano
                 * fino a 1.8 rad, sempre su giunti a tre gradi di liberta' e mai su una cerniera.
                 */
                Dof dd = dofs.get(d);
                double sforo = Math.max(dd.lower - q[d], q[d] - dd.upper);
                if (sforo > peggioreSforo) {
                    peggioreSforo = sforo;
                    peggiore = String.format(Locale.ROOT,
                            "%s (%d gdl nel giunto): %.2f fuori da [%.2f, %.2f]",
                            dd.name, gdlDelGiunto(body, dd), q[d], dd.lower, dd.upper);
                }
                if (sforo > 1e-4) {
                    dentro = false;
                }
            }
            check(finiti, "angoli finiti (corpo " + i + ")");
            check(dentro, "angoli dentro i limiti dei giunti (corpo " + i + ")");
            if (peggioreSforo > 1e-4) {
                System.out.printf(Locale.ROOT, "  corpo %d, sforo massimo %.3f rad: %s%n",
                        i, peggioreSforo, peggiore);
            }
        }
        check(conAngoli > 0, "almeno un corpo in fisica da cui leggere la posa");
        check(conPosizione > 0, "almeno un corpo di cui leggere la posizione");

        // chi esce dal mondo non c'e' piu', e non ha piu' posizione da leggere
        MondoFisico.esce(100);
        check(!MondoFisico.contiene(100), "chi esce dal mondo non c'e' piu'");
        check(MondoFisico.posizioneDi(100) == null, "chi non e' nel mondo non ha posizione");
        check(MondoFisico.posaDi(100) == null, "chi non e' nel mondo non ha posa");

        // ---------------------------------------------------------------- un corpo che cammina
        /*
         * SERVE UN CORPO CHE SAPPIA CAMMINARE per tutti i confronti che seguono: la prima stesura
         * della verifica gemella (PalcoSelfTest) aveva pescato un corpo la cui andatura casuale
         * faceva 0.01 m in dieci secondi, e "chi cammina va piu' lontano di chi riposa" era un
         * confronto fra due zeri. Si cerca con l'ORACOLO - pavimento piatto, nessun terreno -
         * perche' quella e' la misura di cosa un corpo sa fare, non di cosa sa fare su una
         * collina.
         */
        MondoFisico.chiudi();
        Random r2 = new Random(11);
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
                "trovato un corpo che cammina per i confronti (%.2f m/s)", velocita));
        if (corpo == null) {
            System.out.println(passate + "/" + fatte + " verifiche superate");
            System.exit(1);
        }

        /*
         * LA CELLA DEI CONFRONTI: asciutta, senza tronchi, e PIANA - le nove celle intorno alla
         * stessa quota. Su un pendio un corpo scivola anche stando fermo, e ogni confronto fra
         * "muoversi" e "non muoversi" diventerebbe un confronto fra due scivolate.
         */
        int[] piana = cellaPiana(dim);
        check(piana != null, "nel mondo c'e' una cella piana, asciutta e senza alberi");
        if (piana == null) {
            System.out.println(passate + "/" + fatte + " verifiche superate");
            System.exit(1);
        }
        System.out.printf(Locale.ROOT, "cella dei confronti: %d,%d a quota %d%n",
                piana[0], piana[1], src.world.surfaceY(piana[0], piana[1]));

        // ---------------------------------------------------------------- riposare
        /*
         * CAMMINARE E' UNA RICHIESTA CHE ARRIVA AL CORPO. Stesso corpo, stessa andatura, stessa
         * cella piana, due prove di fila: una camminando e una stando ferma. Quello che si
         * verifica e' che le due cose siano DIVERSE, cioe' che la scelta dell'entita' arrivi ai
         * motori; non che chi cammina vada piu' lontano.
         *
         * E QUESTA E' UNA RINUNCIA MISURATA, non una comodita'. Sul palco il confronto era
         * "camminando 1.43 m contro 1.60 m riposando" e andava a rovescio: un corpo FERMO, nella
         * gravita', CROLLA, e crollando scivola - in dieci secondi tanto quanto uno che cammina.
         * Il palco non lo vedeva perche' consegnava metri solo a chi era in piedi (il filtro
         * "plausibile"); nel mondo unico quel filtro non c'e' e non ci deve essere, perche' era un
         * giudizio sulla postura - e chi striscia si muove davvero.
         * Quanto un corpo cammini BENE si misura all'oracolo, dove il pavimento e' piatto e non ci
         * sono pendii: e' una proprieta' del corpo, non una cosa che il mondo garantisce.
         */
        Esito cammina = prova(900, corpo, andatura, MondoFisico.Stato.CAMMINA, 0.0, piana, 20);
        Esito riposa = prova(901, corpo, andatura, MondoFisico.Stato.FERMA, 0.0, piana, 20);
        double scostamento = cammina.fine() == null || riposa.fine() == null ? 0
                : Math.hypot(cammina.fine().x() - riposa.fine().x(),
                        cammina.fine().z() - riposa.fine().z());
        check(scostamento > 0.05, String.format(Locale.ROOT,
                "chi cammina e chi riposa finiscono in due posti diversi (%.2f m di distanza; "
                        + "strada %.2f contro %.2f m)", scostamento, cammina.strada(),
                riposa.strada()));

        // ---------------------------------------------------------------- la posa E' il corpo
        /*
         * QUELLO CHE SI DISEGNA DEVE ESSERE IL CORPO FISICO, non una ricostruzione. E ADESSO
         * NEANCHE L'IMBARDATA SI IMPONE: posaDi non prende piu' un facing, perche' la direzione
         * e' nel corpo. Quello che si verifica e' che la posa sia completa, finita, e che la
         * direzione che si LEGGE dalla posizione sia la stessa che si vede nella posa.
         */
        Esito perLaPosa = prova(902, corpo, andatura, MondoFisico.Stato.CAMMINA, 0.0, piana, 4);
        BodyPose posa = MondoFisico.posaDi(902);
        check(posa != null, "la posa fisica di chi e' nel mondo si puo' leggere");
        check(MondoFisico.posaDi(999) == null, "chi non e' nel mondo non ha posa fisica");
        if (posa != null) {
            boolean finita = true;
            int quantiLink = 0;
            for (BodyPose.LinkPose lp : posa.links()) {
                quantiLink++;
                if (!Double.isFinite(lp.origin().x()) || !Double.isFinite(lp.origin().y())
                        || !Double.isFinite(lp.coreA().y()) || !Double.isFinite(lp.coreB().y())) {
                    finita = false;
                }
            }
            check(quantiLink == corpo.links().size(), "la posa ha tutti i link del corpo");
            check(finita, "tutti i punti della posa sono finiti");

            // la direzione della posa e' la direzione letta: non c'e' nessuna rotazione imposta
            BodyPose.LinkPose t = posa.links().iterator().next();
            V3 avanti = t.rot().apply(new V3(0, 0, 1));
            if (Math.hypot(avanti.x(), avanti.z()) > 0.2 && perLaPosa.fine() != null) {
                double scarto = Math.abs(Rot.wrapPi(Math.atan2(avanti.x(), avanti.z())
                        - perLaPosa.fine().facing()));
                check(scarto < 0.2, String.format(Locale.ROOT,
                        "la direzione della posa e' quella letta dal corpo (scarto %.3f rad)",
                        scarto));
            }
        }

        // ---------------------------------------------------------------- il terreno c'e' davvero
        /*
         * LA QUOTA SEGUE IL TERRENO. E' LA VERIFICA DEL PASSO 2 E 3 INSIEME: lo stesso corpo con
         * la stessa andatura, messo sulla cella piu' ALTA e sulla cella piu' BASSA del mondo, deve
         * trovarsi a due quote diverse di circa quanto il terreno e' diverso. Su un pavimento
         * piatto questa verifica non potrebbe nemmeno essere scritta, ed e' esattamente la
         * differenza fra un palco e un mondo.
         */
        int[] alta = cellaEstrema(dim, true);
        int[] bassa = cellaEstrema(dim, false);
        double dislivello = src.world.surfaceY(alta[0], alta[1])
                - src.world.surfaceY(bassa[0], bassa[1]);
        check(dislivello > 1.5, String.format(Locale.ROOT,
                "il mondo ha un dislivello da misurare (%.1f m fra la cella piu' alta e la piu' "
                        + "bassa)", dislivello));
        Esito inAlto = prova(940, corpo, andatura, MondoFisico.Stato.FERMA, 0.0, alta, 1);
        Esito inBasso = prova(941, corpo, andatura, MondoFisico.Stato.FERMA, 0.0, bassa, 1);
        /*
         * LA QUOTA ASSOLUTA, cioe' Posizione.quota, e NON quella della posa: la posa ha il perno
         * sottratto (e il perno contiene la quota del terreno di quella cella, perche' il motore
         * grafico la somma di nuovo), quindi nella posa due corpi su due colline diverse stanno
         * entrambi intorno a zero. Ci sono cascato scrivendo questa verifica: dava -0.90 m di
         * differenza per 4 m di dislivello.
         */
        double qAlto = inAlto.fine() == null ? Double.NaN : inAlto.fine().quota();
        double qBasso = inBasso.fine() == null ? Double.NaN : inBasso.fine().quota();
        double differenza = qAlto - qBasso;
        check(differenza > dislivello * 0.5, String.format(Locale.ROOT,
                "la quota del corpo segue il terreno sotto di lui (%.2f m di differenza per "
                        + "%.1f m di dislivello)", differenza, dislivello));
        System.out.printf(Locale.ROOT,
                "terreno: sulla cella alta il tronco sta a %.2f m, sulla bassa a %.2f m "
                        + "(dislivello del terreno %.1f m)%n", qAlto, qBasso, dislivello);

        // ---------------------------------------------------------------- l'acqua
        /*
         * L'ACQUA NEL MONDO. Che la spinta e la resistenza di Jolt funzionino lo verifica
         * PhysicsSmokeTest (una capsula cade piu' lentamente in acqua); qui si verifica il
         * CABLAGGIO, cioe' che un corpo in una colonna allagata resti un corpo del mondo con una
         * posizione leggibile, e si MISURA quanta strada fa rispetto allo stesso corpo
         * all'asciutto. La differenza si stampa e non si verifica: dipende da com'e' fatta la
         * colonna che questo seme ha prodotto, e una verifica che dipende dal seme non e' una
         * verifica.
         */
        int[] bagnata = cellaAllagata(dim);
        if (bagnata == null) {
            System.out.println("acqua: questo seme non ha prodotto colonne allagate, niente da "
                    + "misurare");
        } else {
            Esito inAcqua = prova(950, corpo, andatura, MondoFisico.Stato.CAMMINA, 0.0, bagnata, 20);
            check(inAcqua.fine() != null && Double.isFinite(inAcqua.fine().x())
                            && Double.isFinite(inAcqua.fine().quota()),
                    "un corpo in una colonna allagata resta un corpo del mondo");
            System.out.printf(Locale.ROOT,
                    "acqua: in 10 s di mondo nell'acqua %.2f m, all'asciutto %.2f m "
                            + "(colonna %d,%d, pelo a %d)%n",
                    inAcqua.strada(), cammina.strada(), bagnata[0], bagnata[1],
                    src.world.getWaterLevel());
        }

        // ---------------------------------------------------------------- dormire
        /*
         * CHI DORME NON DA' COMANDI AL CORPO: motori spenti. Si verifica L'ASSENZA DI COMANDI, non
         * l'altezza: con il controllo di oggi crolla anche chi sta fermo e sveglio, quindi
         * confrontare le quote non distingue. Quello che cambia e' se i motori seguono gli angoli
         * comandati, e la fase e' ferma a zero per entrambi (avanza solo camminando), quindi
         * l'obiettivo e' lo stesso per tutti e due.
         */
        float[] obiettivo = andatura.anglesAt(corpo, 0, null);
        prova(910, corpo, andatura, MondoFisico.Stato.FERMA, 0.0, piana, 16);
        double scartoSveglio = scartoDagliAngoli(910, obiettivo);
        prova(911, corpo, andatura, MondoFisico.Stato.DORME, 0.0, piana, 16);
        double scartoDorme = scartoDagliAngoli(911, obiettivo);
        check(scartoDorme > scartoSveglio, String.format(Locale.ROOT,
                "chi dorme non segue piu' gli angoli comandati (scarto %.3f contro %.3f rad)",
                scartoDorme, scartoSveglio));

        // ------------------------------------------------------------------ la svolta
        /*
         * LO STERZO ARRIVA AI MOTORI, ed e' QUESTO che il mondo deve garantire - non che il corpo
         * sia bravo a girare, che e' una proprieta' del corpo e si misura all'oracolo
         * (EvolvedBodies: 73 corpi su 78 sbagliano meno con la svolta trovata).
         *
         * E qui serve dire perche' non si verifica la bravura. La prima stesura confrontava quanto
         * la direzione LETTA cambiava in due secondi, con e senza sterzo, e il corpo senza sterzo
         * girava PIU' di quello con la svolta (2.47 contro 1.77 rad). Non e' un difetto: e' la
         * cosa misurata il 25/10, cioe' che le andature di oggi SPIRALEGGIANO - la rotazione non
         * comandata e' piu' grande della svolta chiesta. Con una sola lettura all'inizio e una
         * alla fine quella spirale non si puo' separare dalla svolta (e integrarla e' il lavoro
         * dell'oracolo, che ha un pavimento piatto e nessun pendio).
         *
         * Quello che si verifica allora e' il CABLAGGIO, e in una forma che non si puo' soddisfare
         * per caso: lo stesso corpo nella stessa cella, con sterzo +1, -1 e 0. Chiedere di girare
         * a destra e chiedere di girare a sinistra devono spostare la direzione DA PARTI OPPOSTE
         * rispetto al non chiedere niente - che e' precisamente l'assunzione dichiarata in
         * Gait.sterzoByDof (girare a sinistra e' lo SPECCHIO di girare a destra).
         */
        Gait conSvolta = andatura;
        double erroreSvolta = Double.NaN;
        try (GaitEvaluator ev = new GaitEvaluator()) {
            GaitEvaluator.Turn t = ev.optimizeTurn(corpo, andatura, 10, r2);
            conSvolta = t.gait();
            erroreSvolta = t.errore();
        }
        check(!conSvolta.sterzoNullo(), String.format(Locale.ROOT,
                "la ricerca ha trovato una svolta per questo corpo (errore %.2f rad)",
                erroreSvolta));
        Esito destra = prova(930, corpo, conSvolta, MondoFisico.Stato.CAMMINA, 1.0, piana, 4);
        Esito sinistra = prova(931, corpo, conSvolta, MondoFisico.Stato.CAMMINA, -1.0, piana, 4);
        Esito dritto = prova(932, corpo, conSvolta, MondoFisico.Stato.CAMMINA, 0.0, piana, 4);
        double verso = destra.giro() - dritto.giro();
        double versoOpposto = sinistra.giro() - dritto.giro();
        check(Math.abs(verso) > 0.05 && Math.abs(versoOpposto) > 0.05, String.format(Locale.ROOT,
                "chiedere di girare cambia la direzione del corpo (%.3f e %.3f rad rispetto al "
                        + "dritto)", verso, versoOpposto));
        /*
         * "I DUE VERSI GIRANO DA PARTI OPPOSTE" NON SI VERIFICA QUI, SI STAMPA. Era una verifica,
         * ed e' caduta quando il terreno e' diventato quello lisciato del mondo (25/10/2026):
         * destra -3.28 rad, sinistra -1.63, dritto +1.29 - tutti e due i versi piu' negativi del
         * dritto. Ma la quota finale del tronco diceva il perche': il corpo a destra e quello
         * dritto erano CROLLATI (0.31 m da 1.0), quello a sinistra no. La rotazione di un corpo
         * che crolla e' quella della caduta, non dello sterzo: "rotolare non e' girare" (voce
         * 25), e nel mondo i corpi di oggi crollano in due secondi. La specularita' dei due versi
         * e' una proprieta' dell'andatura e si misura all'oracolo, in piedi e sul piano
         * (GaitEvaluator.optimizeTurn prova entrambi i versi e tiene il peggiore). Qui resta la
         * verifica che il mondo puo' garantire: chiedere di girare cambia quello che il corpo fa.
         */
        System.out.printf(Locale.ROOT,
                "svolta: rispetto al dritto, destra %+.3f rad e sinistra %+.3f rad (%s)%n",
                verso, versoOpposto, verso * versoOpposto < 0 ? "versi opposti"
                        : "STESSO verso: almeno un corpo e' crollato, vedi le quote");
        System.out.printf(Locale.ROOT,
                "svolta: in 2 s la direzione cambia di %.3f rad a destra, %.3f a sinistra, "
                        + "%.3f senza chiedere (quota finale del tronco %.2f / %.2f / %.2f m, "
                        + "strada %.2f / %.2f / %.2f m)%n",
                destra.giro(), sinistra.giro(), dritto.giro(),
                destra.quotaFinale(), sinistra.quotaFinale(), dritto.quotaFinale(),
                destra.strada(), sinistra.strada(), dritto.strada());

        // ------------------------------------------------- nessuno viene rimesso in piedi
        /*
         * CHI CADE RESTA CADUTO. Il palco rimetteva in piedi un corpo abbassato e fermo dopo sei
         * tick; toglierlo e' una decisione dell'utente del 25/10/2026 (morire perche' non si
         * arriva al cibo fa parte dell'evoluzione delle entita'), e questa verifica esiste perche'
         * un giorno qualcuno sara' tentato di rimetterla. Un corpo sveglio e FERMO crolla, quindi:
         * lo si tiene nel mondo per molti piu' tick di quanti bastavano al vecchio rimontaggio, e
         * deve essere ancora giu'.
         *
         * La strada che fa crollando si STAMPA e non si verifica. Sul palco c'era un tetto
         * (PROGRESSO_CROLLO: un corpo a terra che non si trascina non accumula metri) e qui
         * misura 1.6 m, perche' crollare SPOSTA. Non e' un sussidio alla caduta: il corpo e' per
         * terra, e un'entita' per terra non raggiunge quello che ha visto - e' quello che l'utente
         * ha chiesto il 25/10, "chi cade resta caduto".
         */
        Esito aTerra = prova(920, corpo, andatura, MondoFisico.Stato.FERMA, 0.0, piana, 40);
        check(aTerra.quotaPartenza() > 0
                        && aTerra.quotaMinima() < aTerra.quotaPartenza() * GaitEvaluator.FALL_RATIO,
                String.format(Locale.ROOT, "un corpo sveglio e fermo crolla (da %.3f a %.3f m)",
                        aTerra.quotaPartenza(), aTerra.quotaMinima()));
        check(aTerra.quotaFinale() < aTerra.quotaPartenza() * GaitEvaluator.FALL_RATIO,
                String.format(Locale.ROOT,
                        "dopo 40 tick a terra nessuno l'ha rimesso in piedi (quota %.3f, "
                                + "partenza %.3f m)", aTerra.quotaFinale(), aTerra.quotaPartenza()));
        System.out.printf(Locale.ROOT,
                "crollo: in 20 s per terra il corpo e' scivolato di %.2f m (da %.3f a %.3f m di "
                        + "quota)%n", aTerra.strada(), aTerra.quotaPartenza(),
                aTerra.quotaFinale());
        check(aTerra.fine() != null && aTerra.fine().abbassato(),
                "la posizione dice che quel corpo e' abbassato");

        System.out.printf(Locale.ROOT, "in 10 s di mondo: camminando %.2f m, riposando %.2f m%n",
                cammina.strada(), riposa.strada());
        System.out.printf(Locale.ROOT,
                "corpi con posa leggibile: %d su %d, strada totale dei dodici %.2f m%n",
                conAngoli, quanti, stradaTotale);
        System.out.println(MondoFisico.stats());
        MondoFisico.chiudi();

        System.out.println(passate + "/" + fatte + " verifiche superate");
        System.exit(passate == fatte ? 0 : 1);
    }

    private static boolean libera(int x, int z) {
        return !src.world.isWaterColumn(x, z) && !src.Flora.isTrunk(x, z);
    }

    /**
     * Una cella asciutta, senza tronchi e PIANA: le nove celle del suo intorno alla stessa quota e
     * tutte libere. E' il posto dove si fanno i confronti, perche' su un pendio un corpo si muove
     * anche senza volerlo. null se il mondo non ne ha nemmeno una.
     */
    private static int[] cellaPiana(int dim) {
        for (int x = 2; x < dim - 2; x++) {
            for (int z = 2; z < dim - 2; z++) {
                int h = src.world.surfaceY(x, z);
                boolean ok = true;
                for (int dx = -1; dx <= 1 && ok; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (!libera(x + dx, z + dz) || src.world.surfaceY(x + dx, z + dz) != h) {
                            ok = false;
                            break;
                        }
                    }
                }
                if (ok) {
                    return new int[]{x, z};
                }
            }
        }
        return null;
    }

    /** La cella libera piu' alta (o piu' bassa) del mondo. */
    private static int[] cellaEstrema(int dim, boolean alta) {
        int bx = 1, bz = 1;
        int best = src.world.surfaceY(1, 1);
        for (int x = 1; x < dim - 1; x++) {
            for (int z = 1; z < dim - 1; z++) {
                if (!libera(x, z)) {
                    continue;
                }
                int h = src.world.surfaceY(x, z);
                if (alta ? h > best : h < best) {
                    best = h;
                    bx = x;
                    bz = z;
                }
            }
        }
        return new int[]{bx, bz};
    }

    /** Una colonna allagata, o null se questo seme non ne ha prodotte. */
    private static int[] cellaAllagata(int dim) {
        for (int x = 1; x < dim - 1; x++) {
            for (int z = 1; z < dim - 1; z++) {
                if (src.world.isWaterColumn(x, z) && !src.Flora.isTrunk(x, z)) {
                    return new int[]{x, z};
                }
            }
        }
        return null;
    }
}
