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
            candidati.add(new Palco.Candidato(100 + i, body, Gait.seed(body, rng)));
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

        System.out.println(Palco.stats());
        System.out.printf(Locale.ROOT, "corpi con posa leggibile: %d su %d, metri consegnati %.3f%n",
                conAngoli, quanti, metriTotali);
        Palco.chiudi();

        System.out.println(passate + "/" + fatte + " verifiche superate");
        System.exit(passate == fatte ? 0 : 1);
    }
}
