package src;

import src.body.Automaton;
import src.body.BodyTraits;
import src.body.Gaze;
import src.genetics.BodyPlan;

import java.util.Random;

/**
 * Verifiche della vista a cono e della deduzione della testa, senza far girare la simulazione.
 *
 * Perche' serve: nella geometria del cono un errore di SEGNO non si vede guardando lo schermo -
 * l'entita' si muove, sembra viva, e intanto il cervello riceve "cibo a destra" quando il cibo e'
 * a sinistra. Un errore cosi' non si manifesta come un difetto ma come una popolazione che impara
 * male, ed e' indistinguibile da un problema di taratura. Meglio verificarlo qui.
 *
 *   java -cp target\classes src.VisionSelfTest
 */
public final class VisionSelfTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        cone();
        symmetry();
        headOfHumanoid();
        headOfEvolvedBodies();

        System.out.println(passed + "/" + (passed + failed) + " verifiche superate");
        if (failed > 0) {
            System.exit(1);
        }
    }

    /** Il cono vede davanti e non vede dietro. */
    private static void cone() {
        double half = Math.toRadians(90) / 2;       // cono di 90 gradi, guardando verso +z
        check("davanti e' nel cono", Vision.sectorOf(0, 5, 0, half) >= 0);
        check("dietro non e' nel cono", Vision.sectorOf(0, -5, 0, half) < 0);
        check("90 gradi a destra e' fuori", Vision.sectorOf(5, 0, 0, half) < 0);
        check("20 gradi a destra e' dentro",
                Vision.sectorOf((int) Math.round(5 * Math.sin(0.35)), (int) Math.round(5 * Math.cos(0.35)), 0, half) >= 0);

        // guardando verso +x (gaze = pi/2) il cono si sposta con lo sguardo
        double gaze = Math.PI / 2;
        check("girando lo sguardo si vede di lato", Vision.sectorOf(5, 0, gaze, half) >= 0);
        check("girando lo sguardo non si vede piu' avanti", Vision.sectorOf(0, 5, gaze, half) < 0);
    }

    /**
     * Destra e sinistra non si scambiano, e il centro del cono sta al centro della retina. I
     * settori vanno da 0 (sinistra) a SECTORS-1 (destra), con lo stesso significato rispetto al
     * muso qualunque sia la direzione dello sguardo.
     */
    private static void symmetry() {
        double half = Math.toRadians(120) / 2;
        int ahead = Vision.sectorOf(0, 10, 0, half);
        check("davanti cade nei settori centrali", ahead == Vision.SECTORS / 2 || ahead == Vision.SECTORS / 2 - 1);

        int right = Vision.sectorOf(6, 6, 0, half);     // 45 gradi a destra
        int left = Vision.sectorOf(-6, 6, 0, half);     // 45 gradi a sinistra
        check("destra sta nella metà destra della retina", right > ahead);
        check("sinistra sta nella metà sinistra", left < ahead);
        check("destra e sinistra sono simmetriche", (right - ahead) == (ahead - left) || Math.abs((right - ahead) - (ahead - left)) <= 1);

        // la stessa scena, con corpo e sguardo girati, dà gli stessi settori: è il senso della
        // vista egocentrica
        for (double g = -3.0; g <= 3.0; g += 0.5) {
            double dx = 10 * Math.sin(g), dz = 10 * Math.cos(g);
            int s = Vision.sectorOf((int) Math.round(dx), (int) Math.round(dz), g, half);
            check("oggetto davanti al muso, sguardo " + String.format(java.util.Locale.ROOT, "%.1f", g),
                    s == Vision.SECTORS / 2 || s == Vision.SECTORS / 2 - 1);
        }

        check("wrap riporta mezzo giro su mezzo giro",
                Math.abs(Math.abs(Vision.wrap(3 * Math.PI)) - Math.PI) < 1e-9);
        check("wrap di zero", Math.abs(Vision.wrap(0)) < 1e-9);
    }

    /** In un umanoide la testa e' il cranio, e il collo puo' girare. */
    private static void headOfHumanoid() {
        Automaton body = Automaton.fromPlan(
                BodyPlan.humanoid(0.8, 0.15, 0.25, 1.00, 0.08, 0.15, 0.08, 0.20), 1.0);
        Gaze.Head h = Gaze.of(body, BodyTraits.of(body));
        check("la testa dell'umanoide e' il cranio", "head".equals(h.head()));
        check("l'umanoide puo' girare la testa", h.canTurn());
        check("la portata del collo e' sensata (0 < r <= 90 gradi)",
                h.yawRange() > 0 && h.yawRange() <= Math.PI / 2 + 1e-9);
        check("c'e' almeno un giunto su cui girare", !h.yawDofs().isEmpty());
        double share = 0;
        for (Gaze.YawDof d : h.yawDofs()) {
            share += d.share();
            check("l'indice del giunto esiste", d.index() >= 0 && d.index() < body.dofCount());
        }
        check("le quote di rotazione sommano a 1", Math.abs(share - 1.0) < 1e-6);
    }

    /**
     * Su corpi nati da mutazioni: la testa si trova sempre (o il corpo dichiara che non puo'
     * guardarsi intorno), la portata non e' mai assurda, e niente va in eccezione.
     */
    private static void headOfEvolvedBodies() {
        Random rng = new Random(7);
        int rigid = 0, flexible = 0;
        for (int i = 0; i < 200; i++) {
            // si parte da un corpo plausibile e si lascia lavorare la mutazione, come fa
            // l'evoluzione vera (vedi src.body.EvolvedBodies): dopo sei generazioni la forma non
            // ha piu' molto a che vedere con quella di partenza
            BodyPlan plan = BodyPlan.humanoid(
                    0.6 + rng.nextDouble() * 0.5, 0.05 + rng.nextDouble() * 0.25,
                    0.15 + rng.nextDouble() * 0.25, 0.7 + rng.nextDouble() * 0.7,
                    0.04 + rng.nextDouble() * 0.10, 0.08 + rng.nextDouble() * 0.15,
                    0.05 + rng.nextDouble() * 0.10, 0.12 + rng.nextDouble() * 0.15);
            for (int m = 0; m < 6; m++) {
                plan = plan.mutate(rng, BodyPlan.MutationRates.DEFAULT);
            }
            Automaton body = Automaton.fromPlan(plan, 1.0);
            Gaze.Head h = Gaze.of(body, BodyTraits.of(body));
            check("la testa e' un segmento vero", body.link(h.head()) != null);
            check("portata nei limiti", h.yawRange() >= 0 && h.yawRange() <= Math.PI / 2 + 1e-9);
            check("coerenza tra portata e giunti", h.canTurn() == (h.yawRange() > 1e-3 && !h.yawDofs().isEmpty()));
            if (h.canTurn()) {
                flexible++;
            } else {
                rigid++;
            }
        }
        System.out.println("corpi evoluti: " + flexible + " possono guardarsi intorno, " + rigid + " hanno il collo rigido");
        check("qualche corpo evoluto puo' girare la testa", flexible > 0);
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FALLITA: " + what);
        }
    }
}
