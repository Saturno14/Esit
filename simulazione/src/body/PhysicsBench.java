package src.body;

import src.genetics.BodyPlan;
import src.genetics.Genome;
import src.genetics.GenomeSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Misura quanto costa la fisica dei corpi articolati, per decidere COME usarla nella
 * simulazione (vedi STATO_LAVORO.txt): un mondo fisico sempre attivo con tutte le entita'
 * dentro, oppure la fisica usata come "oracolo" che misura l'andatura di un corpo una volta
 * sola e poi la simulazione riusa i numeri.
 *
 *   java -cp "<classpath>" src.body.PhysicsBench [N=1] [secondi=3] [tipo=umanoide|evoluti] [moto=mosso|fermo]
 *
 * ATTENZIONE alla misura: Jolt mette a dormire i corpi fermi, e un corpo che dorme non costa
 * quasi niente. Un corpo caduto a terra si addormenta, quindi misurare corpi inerti da' numeri
 * molto piu' bassi del vero. Con "mosso" (predefinito) tutti i giunti vengono pilotati da una
 * sinusoide a ogni passo: nessuno si addormenta ed e' il caso che conta davvero, perche' nella
 * simulazione le entita' camminano.
 *
 * Stampa: corpi rigidi e vincoli creati, millisecondi per passo diviso tra fisica e controllo
 * dei motori, fattore di tempo reale e quota finale del bacino (per vedere se sono in piedi).
 */
public final class PhysicsBench {

    /** Quanto tempo di simulazione per un corpo, se la fisica serve solo a misurare l'andatura. */
    public static final double ORACLE_SECONDS = 3.0;

    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        double seconds = args.length > 1 ? Double.parseDouble(args[1]) : 3.0;
        String kind = args.length > 2 ? args[2] : "umanoide";
        boolean moving = args.length <= 3 || !"fermo".equals(args[3]);

        PhysicsNatives.require();
        System.out.println("Jolt " + PhysicsNatives.version() + ", "
                + Runtime.getRuntime().availableProcessors() + " core disponibili");

        GenomeSchema schema = GenomeSchema.loadFromClasspath("/gene_schema.json");
        List<Automaton> plans = new ArrayList<>();
        if ("evoluti".equals(kind)) {
            // corpi davvero diversi: mutazioni strutturali forti, come in BodyTool
            Genome.BODY_MUTATION = BodyPlan.MutationRates.WILD;
            for (int k = 0; k < n; k++) {
                Random rng = new Random(1000 + k);
                Genome g = Genome.random(schema, rng, "b" + k, 0);
                for (int gen = 0; gen < 4; gen++) {
                    g = Genome.reproduce(g, Genome.random(schema, rng, "p" + k + gen, 0), schema, rng, "c" + k + gen, gen);
                }
                plans.add(Automaton.fromPlan(g.bodyPlan(schema), 1.0));
            }
        } else {
            Automaton one = Automaton.humanoid(BodyParams.midpoint(schema));
            for (int k = 0; k < n; k++) {
                plans.add(one);
            }
        }

        int perBody = 0;
        for (Automaton a : plans) {
            perBody = Math.max(perBody, a.links().size());
        }
        int maxBodies = Math.max(64, n * (perBody + 2) + 8);

        try (PhysicsWorld world = new PhysicsWorld(maxBodies, 0)) {
            world.addGround(Math.max(50f, n * 0.5f), 0f, 0.9f);

            List<JoltRagdoll> dolls = new ArrayList<>();
            int side = (int) Math.ceil(Math.sqrt(n));
            long buildStart = System.nanoTime();
            for (int k = 0; k < n; k++) {
                double x = (k % side) * 3.0 - side * 1.5;
                double z = (k / side) * 3.0 - side * 1.5;
                dolls.add(JoltRagdoll.spawn(world, plans.get(k), x, 0, z, 0, k + 1));
            }
            long buildMs = (System.nanoTime() - buildStart) / 1_000_000;
            world.optimize();

            int rigid = 0, constraints = 0, dofs = 0;
            for (JoltRagdoll d : dolls) {
                rigid += d.rigidBodyCount();
                constraints += d.constraintCount();
                dofs += d.automaton.dofCount();
            }
            System.out.printf(Locale.ROOT,
                    "%d corpi articolati (%s): %d corpi rigidi, %d vincoli, %d gradi di liberta'; costruiti in %d ms%n",
                    n, kind, rigid, constraints, dofs, buildMs);

            int steps = (int) Math.round(seconds / PhysicsWorld.TIME_STEP);

            // fasi fisse per grado di liberta': una sinusoide diversa per ogni giunto, cosi' il
            // corpo si agita come se stesse camminando e non si addormenta mai
            float[][] phase = new float[n][];
            float[][] q = new float[n][];
            for (int k = 0; k < n; k++) {
                int d = plans.get(k).dofCount();
                phase[k] = new float[d];
                q[k] = new float[d];
                Random pr = new Random(7777 + k);
                for (int i = 0; i < d; i++) {
                    phase[k][i] = (float) (pr.nextDouble() * 2 * Math.PI);
                }
            }

            // qualche passo a vuoto: la prima volta Jolt alloca e la JVM compila
            for (int i = 0; i < 20; i++) {
                if (moving) {
                    drive(dolls, phase, q, i * PhysicsWorld.TIME_STEP);
                }
                world.step();
            }
            long controlNs = 0, physicsNs = 0;
            for (int i = 0; i < steps; i++) {
                if (moving) {
                    long c0 = System.nanoTime();
                    drive(dolls, phase, q, i * PhysicsWorld.TIME_STEP);
                    controlNs += System.nanoTime() - c0;
                }
                long p0 = System.nanoTime();
                world.step();
                physicsNs += System.nanoTime() - p0;
            }
            double elapsedMs = (controlNs + physicsNs) / 1e6;

            double msPerStep = elapsedMs / steps;
            double realtime = (steps * PhysicsWorld.TIME_STEP * 1000.0) / elapsedMs;
            double standing = 0;
            double sumY = 0;
            for (JoltRagdoll d : dolls) {
                double y = d.rootPosition().y();
                sumY += y;
                if (y > 0.5 * plans.get(dolls.indexOf(d)).groundOffset()) {
                    standing++;
                }
            }
            System.out.printf(Locale.ROOT,
                    "%d passi da %.1f ms (%s): %.3f ms per passo = %.3f fisica + %.3f controllo motori"
                            + " (%.3f ms per corpo), tempo reale x%.1f%n",
                    steps, PhysicsWorld.TIME_STEP * 1000, moving ? "giunti in movimento" : "corpi inerti",
                    msPerStep, physicsNs / 1e6 / steps, controlNs / 1e6 / steps,
                    msPerStep / Math.max(1, n), realtime);
            System.out.printf(Locale.ROOT, "corpi attivi (non addormentati) alla fine: %d su %d%n",
                    world.activeBodyCount(), world.bodyCount());
            System.out.printf(Locale.ROOT, "quota media del bacino alla fine: %.3f m; in piedi (meta' altezza): %.0f/%d%n",
                    sumY / Math.max(1, n), standing, n);
            System.out.printf(Locale.ROOT,
                    "Costo come oracolo di andatura (%.1f s di fisica per UN corpo): %.1f ms%n",
                    ORACLE_SECONDS, msPerStep / Math.max(1, n) * (ORACLE_SECONDS / PhysicsWorld.TIME_STEP));
            System.out.printf(Locale.ROOT,
                    "Budget di un tick a x50 (10 ms): la fisica di %d entita' ne userebbe %.0f%%%n",
                    n, 100.0 * msPerStep / 10.0);
        }
        System.exit(0);
    }

    /** Pilota tutti i giunti con una sinusoide: tiene i corpi svegli come durante un'andatura. */
    private static void drive(List<JoltRagdoll> dolls, float[][] phase, float[][] q, double t) {
        for (int k = 0; k < dolls.size(); k++) {
            JoltRagdoll d = dolls.get(k);
            List<Dof> dofs = d.automaton.dofs();
            for (int i = 0; i < q[k].length; i++) {
                Dof dof = dofs.get(i);
                double mid = (dof.lower + dof.upper) / 2;
                double amp = (dof.upper - dof.lower) / 2 * 0.6;
                q[k][i] = (float) (mid + amp * Math.sin(2 * Math.PI * 1.2 * t + phase[k][i]));
            }
            d.setTargetAngles(q[k]);
        }
    }
}
