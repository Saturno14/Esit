package com.saturno14.esit.graphics.world;

import com.saturno14.esit.graphics.mesh.Mesh;
import com.saturno14.esit.graphics.mesh.MeshData;
import src.world;

/**
 * La superficie dell'acqua: un quadrato orizzontale per ogni colonna allagata, tutti nella stessa
 * mesh.
 *
 * FINO A ORA L'ACQUA NON VENIVA DISEGNATA AFFATTO. Nella simulazione esiste da sempre - 376
 * colonne allagate su 2500, le entita' ci bevono e non ci possono camminare - ma a schermo non
 * c'era niente: si vedeva una buca di terra. Era il problema piu' grosso per capire il mondo
 * guardandolo, perche' met&agrave; delle morti per sete non si potevano giudicare senza sapere
 * dove fosse l'acqua.
 *
 * PERCHE' NON UN UNICO PIANO GRANDE. Un solo quadrato esteso su tutta la mappa all'altezza
 * dell'acqua sarebbe una riga di codice, ma coprirebbe anche la terra piu' bassa del livello
 * dell'acqua che non e' allagata, e nasconderebbe il terreno dove invece si cammina. Le colonne
 * allagate sono quelle che la simulazione considera acqua: disegnare esattamente quelle vuol dire
 * che cio' che si vede e cio' su cui le entita' ragionano sono la stessa cosa.
 *
 * SI RIFA' SOLO QUANDO IL TERRENO CAMBIA (world.getTerrainVersion), non a ogni frame.
 */
public final class WaterSurface {

    /** Mezza cella: la superficie del terreno sta a meta' tra l'ultima terra e la prima aria. */
    private static final float SURFACE_OFFSET = 0.5f;
    /**
     * Quanto sotto il livello nominale sta il pelo dell'acqua, in celle. Un filo sotto, cosi' la
     * riva non e' una linea perfettamente coincidente col bordo del terreno: due superfici
     * complanari producono lo z-fighting, quella striscia tremolante lungo tutte le rive.
     */
    private static final float WATER_DROP = 0.08f;

    private Mesh mesh;
    private int builtVersion = -1;
    private int quads;

    /** Rifa' la mesh se il terreno e' cambiato. Da chiamare prima di disegnare. */
    public void update() {
        int version = world.getTerrainVersion();
        if (version == builtVersion && mesh != null) {
            return;
        }
        builtVersion = version;
        MeshData data = build();
        if (data == null) {
            if (mesh != null) {
                mesh.destroy();
                mesh = null;
            }
            quads = 0;
            return;
        }
        if (mesh == null) {
            mesh = new Mesh(data);
        } else {
            mesh.upload(data);
        }
    }

    private MeshData build() {
        int dim = world.getDim();
        int count = 0;
        for (int x = 0; x < dim; x++) {
            for (int z = 0; z < dim; z++) {
                if (world.isWaterColumn(x, z)) {
                    count++;
                }
            }
        }
        quads = count;
        if (count == 0) {
            return null;
        }

        float y = world.getWaterLevel() - SURFACE_OFFSET - WATER_DROP;
        float[] pos = new float[count * 4 * 3];
        float[] nor = new float[count * 4 * 3];
        float[] uv = new float[count * 4 * 2];
        int[] idx = new int[count * 6];
        int p = 0;
        int q = 0;
        int i = 0;
        int v = 0;

        for (int x = 0; x < dim; x++) {
            for (int z = 0; z < dim; z++) {
                if (!world.isWaterColumn(x, z)) {
                    continue;
                }
                // il quadrato copre esattamente la cella, nello stesso sistema del terreno
                float x0 = x - SURFACE_OFFSET;
                float x1 = x + 1 - SURFACE_OFFSET;
                float z0 = z - SURFACE_OFFSET;
                float z1 = z + 1 - SURFACE_OFFSET;
                float[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
                for (float[] c : corners) {
                    pos[p] = c[0];
                    pos[p + 1] = y;
                    pos[p + 2] = c[1];
                    nor[p] = 0;
                    nor[p + 1] = 1;
                    nor[p + 2] = 0;
                    p += 3;
                    // UV in coordinate MONDO: l'onda e' continua da una cella all'altra invece di
                    // ripartire da zero su ogni quadrato, che si vedrebbe come una scacchiera
                    uv[q] = c[0];
                    uv[q + 1] = c[1];
                    q += 2;
                }
                idx[i++] = v;
                idx[i++] = v + 1;
                idx[i++] = v + 2;
                idx[i++] = v;
                idx[i++] = v + 2;
                idx[i++] = v + 3;
                v += 4;
            }
        }
        return new MeshData(pos, nor, idx, uv);
    }

    public void render() {
        if (mesh != null) {
            mesh.render();
        }
    }

    /** Quante celle d'acqua si stanno disegnando (diagnostica). */
    public int cells() {
        return quads;
    }

    public void destroy() {
        if (mesh != null) {
            mesh.destroy();
            mesh = null;
        }
    }
}
