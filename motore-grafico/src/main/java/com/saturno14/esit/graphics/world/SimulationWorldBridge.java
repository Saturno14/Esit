package com.saturno14.esit.graphics.world;

import src.world;

/**
 * Ponte tra la simulazione (src.world) e il campo di densita' del motore grafico.
 *
 * Questa classe LEGGE soltanto: la simulazione non sa di essere disegnata.
 *
 * LA SUPERFICIE DISEGNATA E' UNA LISCIATURA DI QUELLA GIOCATA, e va detto chiaramente. La
 * simulazione ha un'altezza INTERA per colonna: cosi' com'e', il terreno sono gradini da un
 * metro. Qui l'altezza di ogni colonna viene mediata con quella delle colonne vicine e la
 * densita' delle celle attorno alla superficie diventa frazionaria, quindi la superficie estratta
 * e' continua e passa entro mezza cella dall'altezza vera. Le entita' continuano a camminare
 * sulle altezze intere: e' solo il disegno che e' morbido, come in tutti i voxel a superficie
 * liscia.
 *
 * SI RILEGGE SOLO QUANDO SERVE. Prima il mondo intero veniva riletto a ogni frame - a lato 50
 * sono 125000 letture per frame - e ci si affidava al confronto del valore per non rifare la
 * mesh. Adesso si guarda world.getTerrainVersion(): se il terreno non e' cambiato non si legge
 * niente.
 */
public class SimulationWorldBridge {

    private final WorldGrid grid;
    private boolean simulationReady = false;
    private int lastTerrainVersion = Integer.MIN_VALUE;

    public SimulationWorldBridge() {
        simulationReady = world.world_setup();

        int dim = simulationReady ? world.getDim() : 24;
        this.grid = new WorldGrid(dim);

        if (simulationReady) {
            syncFromSimulation();
        } else {
            System.err.println("[SimulationWorldBridge] world_setup() ha fallito: "
                    + "uso un terreno demo come fallback.");
            grid.generateDemoTerrain();
        }
    }

    public WorldGrid getGrid() {
        return grid;
    }

    public boolean isSimulationReady() {
        return simulationReady;
    }

    /** Livello dell'acqua in celle (serve al disegno dell'acqua). */
    public float getWaterLevel() {
        return simulationReady ? world.getWaterLevel() : 0f;
    }

    /**
     * Aggiorna il campo di densita' se il terreno della simulazione e' cambiato. Chiamarla a
     * ogni frame costa un confronto di interi quando non c'e' niente da fare.
     */
    public void syncFromSimulation() {
        if (!simulationReady) {
            return;
        }
        int version = world.getTerrainVersion();
        if (version == lastTerrainVersion) {
            return;
        }
        lastTerrainVersion = version;

        int dim = grid.getDimension();
        float waterLevel = world.getWaterLevel();

        /*
         * LA QUOTA LISCIATA LA DA' LA SIMULAZIONE (world.quotaTerreno), non questo ponte. La
         * formula stava qui, e la fisica non la vedeva: due terreni diversi (voce 26 del diario).
         * Il ponte continua a leggere soltanto.
         */
        float[][] smooth = new float[dim][dim];
        for (int x = 0; x < dim; x++) {
            for (int z = 0; z < dim; z++) {
                smooth[x][z] = (float) world.quotaTerreno(x, z);
            }
        }

        for (int x = 0; x < dim; x++) {
            for (int z = 0; z < dim; z++) {
                float h = smooth[x][z];
                float slope = slopeAt(smooth, x, z, dim);
                for (int y = 0; y < dim; y++) {
                    // densita': piena sotto la superficie, vuota sopra, frazionaria a cavallo
                    float d = Math.max(0f, Math.min(1f, h - y + 0.5f));
                    grid.set(x, y, z, d, materialAt(y, h, slope, waterLevel));
                }
            }
        }
    }

    /**
     * Quanto e' ripida la superficie qui (differenza di quota con i vicini). Serve al materiale:
     * su una parete ripida l'erba non attacca e si vede la roccia, che e' la cosa che piu' di
     * tutte fa sembrare un terreno un terreno e non una gelatina verde.
     */
    private static float slopeAt(float[][] h, int x, int z, int dim) {
        float worst = 0;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int nx = x + d[0], nz = z + d[1];
            if (nx >= 0 && nx < dim && nz >= 0 && nz < dim) {
                worst = Math.max(worst, Math.abs(h[nx][nz] - h[x][z]));
            }
        }
        return worst;
    }

    /**
     * Che materiale e' una cella: sabbia sulla riva, roccia in profondita' e sui pendii ripidi,
     * erba in superficie, terra appena sotto.
     */
    private static int materialAt(int y, float height, float slope, float waterLevel) {
        float depth = height - y;
        if (depth > 3.5f) {
            return VoxelMaterial.ROCK;
        }
        if (height <= waterLevel + 1.2f) {
            return VoxelMaterial.SAND;      // riva e fondale
        }
        if (slope > 1.4f) {
            return VoxelMaterial.ROCK;      // parete: l'erba non ci sta
        }
        return depth > 1.2f ? VoxelMaterial.DIRT : VoxelMaterial.GRASS;
    }
}
