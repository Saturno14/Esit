package com.saturno14.esit.graphics.world;

/**
 * Rappresenta il campo scalare 3D usato dal Surface Nets.
 *
 * density[x][y][z]:
 *   1.0f = pieno (terreno)
 *   0.0f = vuoto (aria)
 * Valori intermedi sono ammessi e producono superfici piu' morbide.
 */
public class WorldGrid {

    private final int dimension;
    private final float[][][] density;

    // true quando la griglia e' cambiata dall'ultima generazione di mesh:
    // il renderer lo controlla per sapere se deve ricostruire la mesh.
    private boolean dirty = true;

    public WorldGrid(int dimension) {
        this.dimension = dimension;
        this.density = new float[dimension][dimension][dimension];
    }

    public int getDimension() {
        return dimension;
    }

    /** Ritorna 0 se le coordinate sono fuori dai limiti, invece di lanciare eccezioni. */
    public float getDensity(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= dimension || y >= dimension || z >= dimension) {
            return 0f;
        }
        return density[x][y][z];
    }

    /** Modifica una cella (es. un'entita' che scava o costruisce) e marca la griglia come "sporca". */
    public void setDensity(int x, int y, int z, float value) {
        if (x < 0 || y < 0 || z < 0 || x >= dimension || y >= dimension || z >= dimension) {
            return;
        }
        density[x][y][z] = value;
        dirty = true;
    }

    /**
     * Come setDensity, ma marca la griglia come "sporca" SOLO se il valore e' davvero cambiato.
     * Usata dal sync con la simulazione: permette di rileggere tutto lo stato ad ogni frame
     * senza forzare una rigenerazione della mesh quando nulla e' cambiato.
     */
    public void setDensityIfChanged(int x, int y, int z, float value) {
        if (x < 0 || y < 0 || z < 0 || x >= dimension || y >= dimension || z >= dimension) {
            return;
        }
        if (density[x][y][z] != value) {
            density[x][y][z] = value;
            dirty = true;
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    /**
     * Genera un terreno dimostrativo (una collina ondulata), usato come fallback se la
     * simulazione reale non e' disponibile.
     */
    public void generateDemoTerrain() {
        for (int x = 0; x < dimension; x++) {
            for (int z = 0; z < dimension; z++) {
                double heightNoise = Math.sin(x * 0.4) * 2.0 + Math.cos(z * 0.35) * 2.0;
                int surfaceHeight = (int) (dimension / 3.0 + heightNoise);

                for (int y = 0; y < dimension; y++) {
                    density[x][y][z] = (y <= surfaceHeight) ? 1.0f : 0.0f;
                }
            }
        }
        dirty = true;
    }
}
