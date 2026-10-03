package src;

import java.util.List;

/**
 * Vista a settori (rete a input FISSO). Attorno all'entita' ci sono 8 settori; per ciascuna
 * categoria di oggetto "importante" (mela, acqua, cadavere, altra entita') ogni settore
 * riporta un solo numero: la vicinanza dell'oggetto piu' vicino entro il raggio visivo
 * (gene sightDistance), da 1 (adiacente) verso 0 (al limite del raggio). Se in un settore non
 * c'e' niente di importante vale 0. In piu', 4 valori dicono cosa c'e' nella cella dell'entita'.
 *
 * Il raggio cambia da entita' a entita' ma la dimensione dell'output e' sempre la stessa,
 * quindi tutti i cervelli hanno la stessa topologia (eredita', crossover e salvataggio
 * restano semplici).
 */
public final class Vision {

    public static final int SECTORS = 8;
    public static final int CATEGORIES = 4;
    public static final int CAT_APPLE = 0;
    public static final int CAT_WATER = 1;
    public static final int CAT_CORPSE = 2;
    public static final int CAT_ENTITY = 3;

    /** 8 settori x 4 categorie + 4 valori "qui". */
    public static final int SIZE = SECTORS * CATEGORIES + CATEGORIES;

    private Vision() {
    }

    public static double[] sense(entity self) {
        double radius = self.getStats().sightDistance;
        int[] pos = self.getPos();
        int px = pos[0];
        int py = pos[1];
        int pz = pos[2];
        int dim = world.getDim();

        double[][] sector = new double[CATEGORIES][SECTORS];
        double[] here = new double[CATEGORIES];

        // Mele e acqua: celle del mondo entro il raggio, sullo stesso livello dell'entita'
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            int x = px + dx;
            if (x < 0 || x >= dim) continue;
            for (int dz = -r; dz <= r; dz++) {
                int z = pz + dz;
                if (z < 0 || z >= dim) continue;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius) continue;
                if ("M".equals(world.getSymbol(x, py, z))) {
                    register(sector, here, CAT_APPLE, dx, dz, d, radius);
                }
                if (world.isWaterColumn(x, z)) {
                    register(sector, here, CAT_WATER, dx, dz, d, radius);
                }
            }
        }

        // Cadaveri
        for (Corpse c : Simulation.corpsesSnapshot()) {
            if (c.isConsumed() || c.getY() != py) continue;
            int dx = c.getX() - px;
            int dz = c.getZ() - pz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= radius) {
                register(sector, here, CAT_CORPSE, dx, dz, d, radius);
            }
        }

        // Altre entita' vive
        List<entity> others = Entity_manager.snapshotAlive();
        for (entity o : others) {
            if (o == self || !o.isAlive()) continue;
            int[] op = o.getPos();
            if (op[1] != py) continue;
            int dx = op[0] - px;
            int dz = op[2] - pz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= radius) {
                register(sector, here, CAT_ENTITY, dx, dz, d, radius);
            }
        }

        double[] out = new double[SIZE];
        int idx = 0;
        for (int cat = 0; cat < CATEGORIES; cat++) {
            for (int s = 0; s < SECTORS; s++) {
                out[idx++] = sector[cat][s];
            }
        }
        for (int cat = 0; cat < CATEGORIES; cat++) {
            out[idx++] = here[cat];
        }
        return out;
    }

    /**
     * Distanza dal cibo visibile piu' vicino (mela se non carnivoro, cadavere se non erbivoro),
     * oppure -1 se non ne vede. Serve solo per la ricompensa di avvicinamento.
     */
    public static double nearestFood(entity self) {
        double radius = self.getStats().sightDistance;
        int[] pos = self.getPos();
        int px = pos[0], py = pos[1], pz = pos[2];
        int dim = world.getDim();
        double best = -1;

        boolean eatsApples = self.getStats().diet != src.genetics.DietType.CARNIVORE;
        boolean eatsMeat = self.getStats().diet != src.genetics.DietType.HERBIVORE;

        if (eatsApples) {
            int r = (int) Math.ceil(radius);
            for (int dx = -r; dx <= r; dx++) {
                int x = px + dx;
                if (x < 0 || x >= dim) continue;
                for (int dz = -r; dz <= r; dz++) {
                    int z = pz + dz;
                    if (z < 0 || z >= dim) continue;
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > radius) continue;
                    if ((best < 0 || d < best) && "M".equals(world.getSymbol(x, py, z))) {
                        best = d;
                    }
                }
            }
        }
        if (eatsMeat) {
            for (Corpse c : Simulation.corpsesSnapshot()) {
                if (c.isConsumed() || c.getY() != py) continue;
                double d = Math.sqrt(Math.pow(c.getX() - px, 2) + Math.pow(c.getZ() - pz, 2));
                if (d <= radius && (best < 0 || d < best)) {
                    best = d;
                }
            }
        }
        return best;
    }

    /** Distanza dall'acqua visibile piu' vicina (colonna d'acqua entro il raggio), oppure -1. */
    public static double nearestWater(entity self) {
        double radius = self.getStats().sightDistance;
        int[] pos = self.getPos();
        int px = pos[0], py = pos[1], pz = pos[2];
        int dim = world.getDim();
        double best = -1;

        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            int x = px + dx;
            if (x < 0 || x >= dim) continue;
            for (int dz = -r; dz <= r; dz++) {
                int z = pz + dz;
                if (z < 0 || z >= dim) continue;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius) continue;
                if ((best < 0 || d < best) && world.isWaterColumn(x, z)) {
                    best = d;
                }
            }
        }
        return best;
    }

    private static void register(double[][] sector, double[] here, int cat,
                                 int dx, int dz, double d, double radius) {
        if (dx == 0 && dz == 0) {
            here[cat] = 1.0;
            return;
        }
        double angle = Math.atan2(dz, dx); // (-pi, pi]
        int s = (int) Math.floor((angle + Math.PI) / (2 * Math.PI) * SECTORS);
        if (s >= SECTORS) s = 0;
        if (s < 0) s = 0;
        double closeness = Math.max(0.0, Math.min(1.0, (radius - d + 1.0) / radius));
        if (closeness > sector[cat][s]) {
            sector[cat][s] = closeness;
        }
    }
}
