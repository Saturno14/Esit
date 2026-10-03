package src;

/**
 * Generatore di terreno: una mappa delle altezze con rumore a 2 ottave, riproducibile dal seed.
 * Per ogni colonna (x,z) restituisce l'indice y della PRIMA cella d'aria (le celle sotto sono terra).
 * Il dislivello va da base-3 a base+2 (5 celle al massimo).
 */
public final class TerrainGenerator {

    private static final double LARGE_SCALE = 6.0;   // dimensione (in celle) delle colline
    private static final double SMALL_SCALE = 2.5;   // dettaglio fine

    private TerrainGenerator() {
    }

    public static int[][] generate(int dim, int base, long seed, boolean flat) {
        int[][] heights = new int[dim][dim];
        for (int x = 0; x < dim; x++) {
            for (int z = 0; z < dim; z++) {
                if (flat) {
                    heights[x][z] = base;
                    continue;
                }
                double v = 0.7 * noise(x / LARGE_SCALE, z / LARGE_SCALE, seed)
                        + 0.3 * noise(x / SMALL_SCALE + 100, z / SMALL_SCALE + 100, seed + 1);
                int offset = (int) Math.round(v * 5.0) - 3; // -3 .. +2
                heights[x][z] = base + offset;
            }
        }
        return heights;
    }

    /** Value noise 2D con interpolazione morbida, in [0,1]. */
    private static double noise(double x, double z, long seed) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        double sx = fx * fx * (3 - 2 * fx);
        double sz = fz * fz * (3 - 2 * fz);

        double a = hash(x0, z0, seed);
        double b = hash(x0 + 1, z0, seed);
        double c = hash(x0, z0 + 1, seed);
        double d = hash(x0 + 1, z0 + 1, seed);
        double top = a + (b - a) * sx;
        double bottom = c + (d - c) * sx;
        return top + (bottom - top) * sz;
    }

    private static double hash(int x, int z, long seed) {
        long h = x * 374761393L + z * 668265263L + seed * 1442695040888963407L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= (h >>> 16);
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
}
