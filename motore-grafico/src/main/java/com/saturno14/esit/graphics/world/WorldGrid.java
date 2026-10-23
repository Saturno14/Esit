package com.saturno14.esit.graphics.world;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Il campo scalare 3D del terreno, con un materiale per cella.
 *
 * density[x][y][z]: 1 = pieno, 0 = vuoto, e TUTTO QUELLO CHE STA IN MEZZO conta. E' il punto
 * della faccenda: con densita' solo 0 o 1 la superficie estratta passa sempre esattamente a meta'
 * tra due celle, e il terreno viene a gradini. Con valori frazionari la stessa griglia produce
 * una superficie continua, perche' la soglia la si attraversa dove dice il valore. E' il modo in
 * cui i giochi a voxel con terreno morbido - Space Engineers tra gli altri - ottengono colline
 * lisce da una griglia grossolana.
 *
 * SCAVARE E' ABBASSARE LA DENSITA'. Non serve nient'altro: la mesh esce da qui, quindi togliere
 * materiale e' una scrittura e un chunk da rifare. Per ora nessuno scava, ma il terreno e' gia'
 * fatto per poterlo fare.
 *
 * CHUNK SPORCHI. Rifare la mesh di tutto il mondo a ogni modifica costa troppo per una modifica
 * continua: il volume e' diviso in cubi di {@link #CHUNK} celle di lato e si tiene l'elenco di
 * quelli cambiati, cosi' si rifa' solo quello che serve. Una cella sul bordo di un chunk sporca
 * anche i vicini, perche' la superficie di un chunk dipende anche dalle celle appena fuori.
 */
public class WorldGrid {

    /** Lato di un chunk in celle. 16 e' il compromesso usuale tra numero di mesh e costo di una. */
    public static final int CHUNK = 16;

    private final int dimension;
    private final int chunksPerAxis;
    private final float[][][] density;
    private final byte[][][] material;

    private final Set<Long> dirtyChunks = new LinkedHashSet<>();

    public WorldGrid(int dimension) {
        this.dimension = dimension;
        this.chunksPerAxis = (dimension + CHUNK - 1) / CHUNK;
        this.density = new float[dimension][dimension][dimension];
        this.material = new byte[dimension][dimension][dimension];
        markAllDirty();
    }

    public int getDimension() {
        return dimension;
    }

    public int getChunksPerAxis() {
        return chunksPerAxis;
    }

    /** Ritorna 0 ("vuoto") fuori dai limiti, invece di lanciare eccezioni. */
    public float getDensity(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= dimension || y >= dimension || z >= dimension) {
            return 0f;
        }
        return density[x][y][z];
    }

    /** Materiale di una cella; fuori dai limiti risponde ROCK, che e' il riempimento del mondo. */
    public int getMaterial(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= dimension || y >= dimension || z >= dimension) {
            return VoxelMaterial.ROCK;
        }
        return material[x][y][z];
    }

    /** Scrive densita' e materiale, e sporca i chunk interessati solo se qualcosa e' cambiato. */
    public void set(int x, int y, int z, float value, int mat) {
        if (x < 0 || y < 0 || z < 0 || x >= dimension || y >= dimension || z >= dimension) {
            return;
        }
        if (density[x][y][z] == value && material[x][y][z] == mat) {
            return;
        }
        density[x][y][z] = value;
        material[x][y][z] = (byte) mat;
        markDirtyAround(x, y, z);
    }

    /** Toglie materiale: e' lo scavo. Ritorna quanto ne ha tolto davvero. */
    public float dig(int x, int y, int z, float amount) {
        float before = getDensity(x, y, z);
        float after = Math.max(0f, before - amount);
        if (after != before) {
            density[x][y][z] = after;
            markDirtyAround(x, y, z);
        }
        return before - after;
    }

    public boolean hasDirtyChunks() {
        return !dirtyChunks.isEmpty();
    }

    /** Elenco dei chunk da rifare (copia: il chiamante li consuma e poi li pulisce). */
    public synchronized long[] takeDirtyChunks() {
        long[] out = new long[dirtyChunks.size()];
        int i = 0;
        for (long k : dirtyChunks) {
            out[i++] = k;
        }
        dirtyChunks.clear();
        return out;
    }

    public void markAllDirty() {
        synchronized (this) {
            for (int cx = 0; cx < chunksPerAxis; cx++) {
                for (int cy = 0; cy < chunksPerAxis; cy++) {
                    for (int cz = 0; cz < chunksPerAxis; cz++) {
                        dirtyChunks.add(key(cx, cy, cz));
                    }
                }
            }
        }
    }

    /**
     * Sporca il chunk della cella e, se la cella sta sul bordo, anche i vicini: la superficie di
     * un chunk dipende dalle celle appena fuori dal chunk, quindi dimenticarseli lascia una
     * fessura sulla giuntura.
     */
    private void markDirtyAround(int x, int y, int z) {
        synchronized (this) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int cx = Math.floorDiv(x + dx, CHUNK);
                        int cy = Math.floorDiv(y + dy, CHUNK);
                        int cz = Math.floorDiv(z + dz, CHUNK);
                        if (cx >= 0 && cy >= 0 && cz >= 0
                                && cx < chunksPerAxis && cy < chunksPerAxis && cz < chunksPerAxis) {
                            dirtyChunks.add(key(cx, cy, cz));
                        }
                    }
                }
            }
        }
    }

    public static long key(int cx, int cy, int cz) {
        return ((long) cx << 40) | ((long) cy << 20) | cz;
    }

    public static int keyX(long k) {
        return (int) (k >> 40);
    }

    public static int keyY(long k) {
        return (int) ((k >> 20) & 0xFFFFF);
    }

    public static int keyZ(long k) {
        return (int) (k & 0xFFFFF);
    }

    /**
     * Terreno dimostrativo (colline ondulate), usato se la simulazione non e' disponibile. La
     * densita' e' frazionaria vicino alla superficie, come quella vera.
     */
    public void generateDemoTerrain() {
        for (int x = 0; x < dimension; x++) {
            for (int z = 0; z < dimension; z++) {
                double h = dimension / 3.0 + Math.sin(x * 0.18) * 3.0 + Math.cos(z * 0.15) * 3.0;
                for (int y = 0; y < dimension; y++) {
                    float d = (float) Math.max(0, Math.min(1, h - y + 0.5));
                    int mat = y > h - 1.5 ? VoxelMaterial.GRASS
                            : (y > h - 4 ? VoxelMaterial.DIRT : VoxelMaterial.ROCK);
                    set(x, y, z, d, mat);
                }
            }
        }
        markAllDirty();
    }
}
