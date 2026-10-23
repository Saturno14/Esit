package com.saturno14.esit.graphics.mesh;

import com.saturno14.esit.graphics.world.VoxelMaterial;
import com.saturno14.esit.graphics.world.WorldGrid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estrae la superficie di un CHUNK del campo di densita' con Surface Nets, e per ogni vertice
 * calcola normale, occlusione ambientale e miscela di materiali.
 *
 * COME FUNZIONA SURFACE NETS, in due righe: per ogni cubetto di otto celle attraversato dalla
 * soglia si mette UN vertice, nel punto medio delle intersezioni tra la soglia e gli spigoli del
 * cubetto; poi, per ogni spigolo della griglia attraversato dalla soglia, si collegano con un
 * quadrilatero i quattro cubetti che condividono quello spigolo. Ne esce una superficie
 * continua, senza i casi speciali del marching cubes.
 *
 * TRE SCELTE CHE CAMBIANO L'ASPETTO, e perche':
 *
 * 1) LE NORMALI VENGONO DAL GRADIENTE del campo, non dalla media delle facce dei triangoli. Il
 *    gradiente in un punto non dipende da quali triangoli stanno nel chunk, quindi due chunk
 *    adiacenti calcolano la STESSA normale sulla giuntura e la cucitura non si vede. Con le
 *    normali per faccia, invece, i vertici di bordo appartengono a meno triangoli e la giuntura
 *    appare come una riga di luce diversa.
 *
 * 2) OCCLUSIONE AMBIENTALE PER VERTICE, campionando quanta materia c'e' attorno al vertice nel
 *    verso in cui guarda. E' la cosa che piu' di tutte fa leggere la FORMA del terreno - piu'
 *    delle texture: senza, un avvallamento e una gobba illuminati allo stesso modo si somigliano.
 *
 * 3) QUATTRO PESI DI MATERIALE per vertice invece di un indice. Un vertice sul confine tra erba
 *    e sabbia e' mezzo e mezzo, e lo shader sfuma: i confini tra materiali non sono tagliati con
 *    l'accetta lungo i bordi delle celle.
 *
 * CONFINI TRA CHUNK. Ogni chunk genera i vertici dei cubetti da (inizio-1) a (inizio+lato-1), ma
 * emette i triangoli solo per gli spigoli il cui punto di griglia gli appartiene. Cosi' ogni
 * pezzo di superficie viene disegnato da UN SOLO chunk - niente superfici doppie - e i
 * quadrilateri sul bordo hanno comunque tutti e quattro i vertici che servono, perche' quelli
 * oltre il confine li ricalcola in locale (identici, perche' dipendono solo dal campo).
 */
public final class VoxelMesher {

    private static final float ISO = 0.5f;
    /** Quanto scurisce al massimo l'occlusione ambientale. */
    private static final float AO_STRENGTH = 0.75f;

    private VoxelMesher() {
    }

    /** Superficie di un chunk, o null se il chunk e' tutto pieno o tutto vuoto. */
    public static MeshData generate(WorldGrid grid, int cx, int cy, int cz) {
        int dim = grid.getDimension();
        int side = WorldGrid.CHUNK;
        int sx = cx * side, sy = cy * side, sz = cz * side;

        Map<Long, Integer> indexByCube = new HashMap<>();
        List<Float> positions = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Float> materials = new ArrayList<>();
        List<Float> ao = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        // --- vertici: un cubetto alla volta, compresa una corona di uno oltre il confine
        for (int x = sx - 1; x <= sx + side - 1; x++) {
            for (int y = sy - 1; y <= sy + side - 1; y++) {
                for (int z = sz - 1; z <= sz + side - 1; z++) {
                    float[] v = cubeVertex(grid, x, y, z);
                    if (v == null) {
                        continue;
                    }
                    indexByCube.put(cubeKey(x, y, z), positions.size() / 3);
                    positions.add(v[0]);
                    positions.add(v[1]);
                    positions.add(v[2]);

                    float[] n = gradientNormal(grid, v[0], v[1], v[2]);
                    normals.add(n[0]);
                    normals.add(n[1]);
                    normals.add(n[2]);

                    float[] w = materialWeights(grid, x, y, z);
                    for (float f : w) {
                        materials.add(f);
                    }
                    ao.add(occlusion(grid, v[0], v[1], v[2], n));
                }
            }
        }
        if (indices.isEmpty() && positions.isEmpty()) {
            return null;
        }

        /*
         * --- quadrilateri: uno per spigolo attraversato, solo per gli spigoli di questo chunk
         *
         * IL MONDO E' CHIUSO ANCHE AI SUOI BORDI. Gli spigoli considerati arrivano fino a
         * FUORI dalla griglia: da -1 a 0 sui lati bassi, e da dim-1 a dim su quelli alti.
         * Fuori dalla griglia la densita' vale 0 (vedi WorldGrid.getDensity), quindi la soglia
         * e' attraversata e la superficie si chiude con un fondo e quattro pareti.
         *
         * PERCHE' SERVE. Prima gli spigoli di bordo venivano saltati (il ciclo partiva da 0 e si
         * fermava a dim-2): il terreno era una buccia aperta sotto e di lato. Guardandolo
         * dall'alto sembrava giusto, ma scendendo con la telecamera sotto la superficie non
         * c'era niente - "esiste solo il layer superficiale, sotto e' vuoto" - perche' davvero
         * non era stato disegnato niente, pur essendo il sottosuolo pieno di terra nei dati.
         *
         * Niente superfici doppie: lo spigolo a -1 appartiene solo al chunk che comincia a 0, e
         * quello a dim-1 solo al chunk che lo contiene.
         */
        int maxX = Math.min(sx + side - 1, dim - 1);
        int maxY = Math.min(sy + side - 1, dim - 1);
        int maxZ = Math.min(sz + side - 1, dim - 1);
        for (int x = sx == 0 ? -1 : sx; x <= maxX; x++) {
            for (int y = sy == 0 ? -1 : sy; y <= maxY; y++) {
                for (int z = sz == 0 ? -1 : sz; z <= maxZ; z++) {
                    // spigolo lungo X: cubetti (x, y-1..y, z-1..z)
                    if (crosses(grid, x, y, z, x + 1, y, z)) {
                        quad(indices, indexByCube, positions,
                                cubeKey(x, y - 1, z - 1), cubeKey(x, y, z - 1),
                                cubeKey(x, y, z), cubeKey(x, y - 1, z),
                                grid.getDensity(x, y, z) > ISO);
                    }
                    // spigolo lungo Y
                    if (crosses(grid, x, y, z, x, y + 1, z)) {
                        quad(indices, indexByCube, positions,
                                cubeKey(x - 1, y, z - 1), cubeKey(x, y, z - 1),
                                cubeKey(x, y, z), cubeKey(x - 1, y, z),
                                !(grid.getDensity(x, y, z) > ISO));
                    }
                    // spigolo lungo Z
                    if (crosses(grid, x, y, z, x, y, z + 1)) {
                        quad(indices, indexByCube, positions,
                                cubeKey(x - 1, y - 1, z), cubeKey(x, y - 1, z),
                                cubeKey(x, y, z), cubeKey(x - 1, y, z),
                                grid.getDensity(x, y, z) > ISO);
                    }
                }
            }
        }
        if (indices.isEmpty()) {
            return null;
        }

        return new MeshData(toArray(positions), toArray(normals), toIntArray(indices),
                null, toArray(materials), toArray(ao));
    }

    /** La soglia passa tra queste due celle? */
    private static boolean crosses(WorldGrid g, int x1, int y1, int z1, int x2, int y2, int z2) {
        return (g.getDensity(x1, y1, z1) > ISO) != (g.getDensity(x2, y2, z2) > ISO);
    }

    /**
     * Vertice di un cubetto: media dei punti in cui la soglia taglia i suoi dodici spigoli. Null
     * se il cubetto non e' attraversato (tutto pieno o tutto vuoto).
     */
    private static float[] cubeVertex(WorldGrid g, int x, int y, int z) {
        float sx = 0, sy = 0, sz = 0;
        int n = 0;
        for (int i = 0; i < 12; i++) {
            int[] e = EDGES[i];
            int ax = x + e[0], ay = y + e[1], az = z + e[2];
            int bx = x + e[3], by = y + e[4], bz = z + e[5];
            float da = g.getDensity(ax, ay, az);
            float db = g.getDensity(bx, by, bz);
            if ((da > ISO) == (db > ISO)) {
                continue;
            }
            // dove cade la soglia lungo lo spigolo: e' questo che rende la superficie morbida
            float t = Math.abs(db - da) < 1e-6f ? 0.5f : (ISO - da) / (db - da);
            t = Math.max(0f, Math.min(1f, t));
            sx += ax + (bx - ax) * t;
            sy += ay + (by - ay) * t;
            sz += az + (bz - az) * t;
            n++;
        }
        if (n == 0) {
            return null;
        }
        return new float[]{sx / n, sy / n, sz / n};
    }

    /** Densita' interpolata in un punto qualsiasi (trilineare). */
    private static float sample(WorldGrid g, float x, float y, float z) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        float fx = x - x0, fy = y - y0, fz = z - z0;
        float c00 = lerp(g.getDensity(x0, y0, z0), g.getDensity(x0 + 1, y0, z0), fx);
        float c10 = lerp(g.getDensity(x0, y0 + 1, z0), g.getDensity(x0 + 1, y0 + 1, z0), fx);
        float c01 = lerp(g.getDensity(x0, y0, z0 + 1), g.getDensity(x0 + 1, y0, z0 + 1), fx);
        float c11 = lerp(g.getDensity(x0, y0 + 1, z0 + 1), g.getDensity(x0 + 1, y0 + 1, z0 + 1), fx);
        return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
    }

    /** Normale = gradiente della densita', all'indietro (si esce dalla materia). */
    private static float[] gradientNormal(WorldGrid g, float x, float y, float z) {
        float h = 0.6f;
        float nx = sample(g, x - h, y, z) - sample(g, x + h, y, z);
        float ny = sample(g, x, y - h, z) - sample(g, x, y + h, z);
        float nz = sample(g, x, y, z - h) - sample(g, x, y, z + h);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) {
            return new float[]{0, 1, 0};
        }
        return new float[]{nx / len, ny / len, nz / len};
    }

    /**
     * Quanta materia c'e' attorno al vertice nel verso in cui guarda: 1 = cielo aperto, verso 0
     * = incassato. Si campiona a due distanze lungo la normale e su quattro direzioni inclinate,
     * che e' il minimo per distinguere un avvallamento da un dosso.
     */
    private static float occlusion(WorldGrid g, float x, float y, float z, float[] n) {
        float[] t1 = tangent(n);
        float[] t2 = cross(n, t1);
        float occ = 0;
        float weight = 0;
        float[][] dirs = {
                {n[0], n[1], n[2]},
                {n[0] + t1[0], n[1] + t1[1], n[2] + t1[2]},
                {n[0] - t1[0], n[1] - t1[1], n[2] - t1[2]},
                {n[0] + t2[0], n[1] + t2[1], n[2] + t2[2]},
                {n[0] - t2[0], n[1] - t2[1], n[2] - t2[2]},
        };
        for (float[] d : dirs) {
            float len = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
            if (len < 1e-6f) {
                continue;
            }
            for (float r : new float[]{1.0f, 2.2f}) {
                float w = 1f / r;
                occ += w * sample(g, x + d[0] / len * r, y + d[1] / len * r, z + d[2] / len * r);
                weight += w;
            }
        }
        float f = weight > 0 ? occ / weight : 0;
        return Math.max(0f, 1f - AO_STRENGTH * Math.min(1f, f));
    }

    /**
     * Pesi dei materiali attorno al vertice di un cubetto: conta solo la materia (una cella
     * vuota non ha un materiale che si veda), pesata sulla sua densita'.
     */
    private static float[] materialWeights(WorldGrid g, int x, int y, int z) {
        float[] w = new float[VoxelMaterial.COUNT];
        float total = 0;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    float d = g.getDensity(x + dx, y + dy, z + dz);
                    if (d <= 0.01f) {
                        continue;
                    }
                    w[g.getMaterial(x + dx, y + dy, z + dz)] += d;
                    total += d;
                }
            }
        }
        if (total <= 0) {
            w[VoxelMaterial.ROCK] = 1;
            return w;
        }
        for (int i = 0; i < w.length; i++) {
            w[i] /= total;
        }
        return w;
    }

    /**
     * Due triangoli tra quattro cubetti. L'ordine dei vertici decide da che parte guarda la
     * faccia, e dipende da quale estremo dello spigolo e' pieno: se non si gira, metà superficie
     * viene scartata dal culling e il terreno si vede bucato.
     */
    private static void quad(List<Integer> indices, Map<Long, Integer> indexByCube, List<Float> positions,
                             long a, long b, long c, long d, boolean flip) {
        Integer ia = indexByCube.get(a), ib = indexByCube.get(b);
        Integer ic = indexByCube.get(c), id = indexByCube.get(d);
        if (ia == null || ib == null || ic == null || id == null) {
            return;     // succede solo ai bordi del mondo, dove il quadrilatero non esiste
        }
        if (flip) {
            addTriangle(indices, ia, ib, ic);
            addTriangle(indices, ia, ic, id);
        } else {
            addTriangle(indices, ia, ic, ib);
            addTriangle(indices, ia, id, ic);
        }
    }

    private static void addTriangle(List<Integer> indices, int a, int b, int c) {
        indices.add(a);
        indices.add(b);
        indices.add(c);
    }

    private static float[] tangent(float[] n) {
        // un vettore qualsiasi non parallelo alla normale, poi reso perpendicolare
        float[] up = Math.abs(n[1]) > 0.9f ? new float[]{1, 0, 0} : new float[]{0, 1, 0};
        float[] t = cross(n, up);
        float len = (float) Math.sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2]);
        return len < 1e-6f ? new float[]{1, 0, 0} : new float[]{t[0] / len, t[1] / len, t[2] / len};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static long cubeKey(int x, int y, int z) {
        // +2 perche' gli indici arrivano a -1 (corona oltre il confine) e la chiave non e' firmata
        return (((long) (x + 2)) << 42) | (((long) (y + 2)) << 21) | (z + 2);
    }

    private static float[] toArray(List<Float> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    /** I dodici spigoli di un cubetto, come coppie di vertici (offset 0/1 su x,y,z). */
    private static final int[][] EDGES = {
            {0, 0, 0, 1, 0, 0}, {0, 1, 0, 1, 1, 0}, {0, 0, 1, 1, 0, 1}, {0, 1, 1, 1, 1, 1},
            {0, 0, 0, 0, 1, 0}, {1, 0, 0, 1, 1, 0}, {0, 0, 1, 0, 1, 1}, {1, 0, 1, 1, 1, 1},
            {0, 0, 0, 0, 0, 1}, {1, 0, 0, 1, 0, 1}, {0, 1, 0, 0, 1, 1}, {1, 1, 0, 1, 1, 1},
    };
}
