package com.saturno14.esit.graphics.apple;

import com.saturno14.esit.graphics.mesh.MeshData;

import java.util.ArrayList;
import java.util.List;

/**
 * Genera la mesh di una mela: sfera schiacciata con fossetta in alto (dove si innesta il
 * gambo) e leggera rientranza in basso, piu' gambo (cilindro sottile inclinato) e foglia
 * (rombo visibile da entrambi i lati). Ha coordinate UV per AppleTexture.
 *
 * L'origine e' il punto piu' basso della mela (y = 0), cosi' basta posizionarla sulla
 * superficie del terreno.
 */
public final class AppleMesh {

    private static final float RADIUS = 0.22f;
    private static final float HEIGHT_SCALE = 0.92f;
    private static final int LAT = 14;
    private static final int LON = 20;

    private AppleMesh() {
    }

    public static MeshData create() {
        List<Float> pos = new ArrayList<>();
        List<Float> nor = new ArrayList<>();
        List<Float> uv = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();

        buildBody(pos, nor, uv, idx);
        float stemTopX = buildStem(pos, nor, uv, idx);
        buildLeaf(pos, nor, uv, idx, stemTopX);

        // Origine sul punto piu' basso
        float minY = Float.MAX_VALUE;
        for (int i = 1; i < pos.size(); i += 3) {
            minY = Math.min(minY, pos.get(i));
        }
        for (int i = 1; i < pos.size(); i += 3) {
            pos.set(i, pos.get(i) - minY);
        }

        return new MeshData(toFloat(pos), toFloat(nor), toInt(idx), toFloat(uv));
    }

    private static void buildBody(List<Float> pos, List<Float> nor, List<Float> uv, List<Integer> idx) {
        float b = RADIUS * HEIGHT_SCALE;
        for (int lat = 0; lat <= LAT; lat++) {
            double theta = Math.PI * lat / LAT;
            float v = AppleTexture.STRIP_V + (1f - AppleTexture.STRIP_V) * lat / LAT;
            for (int lon = 0; lon <= LON; lon++) {
                double phi = 2.0 * Math.PI * lon / LON;

                float r = (float) (RADIUS * Math.sin(theta) * (1.0 + 0.07 * Math.cos(theta)));
                float yBase = (float) (b * Math.cos(theta));
                float y = yBase
                        - (float) (RADIUS * 0.30 * Math.exp(-Math.pow(theta / 0.32, 2)))
                        + (float) (RADIUS * 0.10 * Math.exp(-Math.pow((Math.PI - theta) / 0.32, 2)));
                float x = (float) (r * Math.cos(phi));
                float z = (float) (r * Math.sin(phi));

                // normale di un ellissoide: (x/a^2, y/b^2, z/a^2)
                float nx = x / (RADIUS * RADIUS);
                float ny = yBase / (b * b);
                float nz = z / (RADIUS * RADIUS);
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-6f) {
                    nx = 0;
                    ny = 1;
                    nz = 0;
                    len = 1;
                }

                addVertex(pos, nor, uv, x, y, z, nx / len, ny / len, nz / len, (float) lon / LON, v);
            }
        }
        for (int lat = 0; lat < LAT; lat++) {
            for (int lon = 0; lon < LON; lon++) {
                int a = lat * (LON + 1) + lon;
                int bIdx = a + LON + 1;
                idx.add(a);
                idx.add(bIdx);
                idx.add(a + 1);
                idx.add(a + 1);
                idx.add(bIdx);
                idx.add(bIdx + 1);
            }
        }
    }

    /** Gambo: cilindro a 6 lati, leggermente inclinato. Ritorna la X della sua cima. */
    private static float buildStem(List<Float> pos, List<Float> nor, List<Float> uv, List<Integer> idx) {
        int sides = 6;
        float y0 = RADIUS * HEIGHT_SCALE - RADIUS * 0.30f - 0.02f; // dentro la fossetta
        float y1 = RADIUS * HEIGHT_SCALE + 0.06f;
        float tilt = 0.025f;
        float r0 = 0.014f;
        float r1 = 0.011f;
        int start = pos.size() / 3;

        for (int ring = 0; ring < 2; ring++) {
            float y = ring == 0 ? y0 : y1;
            float r = ring == 0 ? r0 : r1;
            float cx = ring == 0 ? 0f : tilt;
            for (int s = 0; s < sides; s++) {
                double a = 2.0 * Math.PI * s / sides;
                float dx = (float) Math.cos(a);
                float dz = (float) Math.sin(a);
                addVertex(pos, nor, uv, cx + r * dx, y, r * dz, dx, 0f, dz, 0.25f, 0.04f);
            }
        }
        for (int s = 0; s < sides; s++) {
            int n = (s + 1) % sides;
            int a = start + s;
            int b = start + n;
            int c = start + sides + s;
            int d = start + sides + n;
            idx.add(a);
            idx.add(b);
            idx.add(c);
            idx.add(c);
            idx.add(b);
            idx.add(d);
        }
        return tilt;
    }

    /** Foglia: rombo appoggiato in cima al gambo, con vertici duplicati per i due lati. */
    private static void buildLeaf(List<Float> pos, List<Float> nor, List<Float> uv, List<Integer> idx,
                                  float stemTopX) {
        float baseY = RADIUS * HEIGHT_SCALE + 0.04f;
        float[][] p = {
                {stemTopX, baseY, 0f},                         // base
                {stemTopX + 0.05f, baseY + 0.030f, 0.035f},    // lato sinistro
                {stemTopX + 0.11f, baseY + 0.040f, 0.010f},    // punta
                {stemTopX + 0.05f, baseY + 0.030f, -0.035f}    // lato destro
        };
        // normale del rombo (piano quasi orizzontale)
        float[] e1 = {p[1][0] - p[0][0], p[1][1] - p[0][1], p[1][2] - p[0][2]};
        float[] e2 = {p[3][0] - p[0][0], p[3][1] - p[0][1], p[3][2] - p[0][2]};
        float nx = e1[1] * e2[2] - e1[2] * e2[1];
        float ny = e1[2] * e2[0] - e1[0] * e2[2];
        float nz = e1[0] * e2[1] - e1[1] * e2[0];
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= len;
        ny /= len;
        nz /= len;
        if (ny < 0) { // normale rivolta verso l'alto
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }

        for (int side = 0; side < 2; side++) {
            float sgn = side == 0 ? 1f : -1f;
            int start = pos.size() / 3;
            for (float[] q : p) {
                addVertex(pos, nor, uv, q[0], q[1], q[2], sgn * nx, sgn * ny, sgn * nz, 0.75f, 0.04f);
            }
            idx.add(start);
            idx.add(start + 1);
            idx.add(start + 2);
            idx.add(start);
            idx.add(start + 2);
            idx.add(start + 3);
        }
    }

    private static void addVertex(List<Float> pos, List<Float> nor, List<Float> uv,
                                  float x, float y, float z, float nx, float ny, float nz, float u, float v) {
        pos.add(x);
        pos.add(y);
        pos.add(z);
        nor.add(nx);
        nor.add(ny);
        nor.add(nz);
        uv.add(u);
        uv.add(v);
    }

    private static float[] toFloat(List<Float> list) {
        float[] a = new float[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }

    private static int[] toInt(List<Integer> list) {
        int[] a = new int[list.size()];
        for (int i = 0; i < a.length; i++) a[i] = list.get(i);
        return a;
    }
}
