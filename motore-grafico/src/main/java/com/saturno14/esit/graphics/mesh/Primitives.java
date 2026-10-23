package com.saturno14.esit.graphics.mesh;

/**
 * Le due forme di base con cui si disegna un corpo articolato: una sfera e un cilindro, entrambi
 * di raggio 1 e centrati/allineati in modo da poter essere piazzati con una sola matrice.
 *
 * Si caricano UNA volta sulla GPU e si riusano per ogni segmento di ogni entita', cambiando solo
 * la matrice modello: cosi' un corpo che cambia posa a ogni frame non costa nessuna rigenerazione
 * di mesh (prima la mesh dell'entita' veniva ricostruita a ogni cambio di eta' con Surface Nets).
 */
public final class Primitives {

    private Primitives() {
    }

    /** Sfera di raggio 1 centrata nell'origine: fa le estremita' dei segmenti e i giunti. */
    public static MeshData sphere(int lat, int lon) {
        int vertices = (lat + 1) * (lon + 1);
        float[] pos = new float[vertices * 3];
        float[] nor = new float[vertices * 3];
        int[] idx = new int[lat * lon * 6];
        int v = 0;
        for (int i = 0; i <= lat; i++) {
            double theta = Math.PI * i / lat;           // 0 = polo nord
            double st = Math.sin(theta), ct = Math.cos(theta);
            for (int j = 0; j <= lon; j++) {
                double phi = 2 * Math.PI * j / lon;
                float x = (float) (st * Math.cos(phi));
                float y = (float) ct;
                float z = (float) (st * Math.sin(phi));
                pos[v] = x; pos[v + 1] = y; pos[v + 2] = z;
                nor[v] = x; nor[v + 1] = y; nor[v + 2] = z;   // sfera unitaria: normale = posizione
                v += 3;
            }
        }
        int k = 0;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                int a = i * (lon + 1) + j;
                int b = a + lon + 1;
                idx[k++] = a;     idx[k++] = b;     idx[k++] = a + 1;
                idx[k++] = a + 1; idx[k++] = b;     idx[k++] = b + 1;
            }
        }
        return new MeshData(pos, nor, idx);
    }

    /**
     * Cilindro di raggio 1 e altezza 1, dall'origine verso +Y, senza coperchi (le estremita' le
     * coprono le sfere). Scalato con (raggio, lunghezza, raggio) diventa un segmento del corpo:
     * le normali restano orizzontali, quindi la luce resta corretta anche con scala non uniforme.
     */
    public static MeshData cylinder(int sides) {
        int vertices = (sides + 1) * 2;
        float[] pos = new float[vertices * 3];
        float[] nor = new float[vertices * 3];
        int[] idx = new int[sides * 6];
        int v = 0;
        for (int j = 0; j <= sides; j++) {
            double phi = 2 * Math.PI * j / sides;
            float x = (float) Math.cos(phi);
            float z = (float) Math.sin(phi);
            pos[v] = x; pos[v + 1] = 0f; pos[v + 2] = z;
            nor[v] = x; nor[v + 1] = 0f; nor[v + 2] = z;
            v += 3;
            pos[v] = x; pos[v + 1] = 1f; pos[v + 2] = z;
            nor[v] = x; nor[v + 1] = 0f; nor[v + 2] = z;
            v += 3;
        }
        int k = 0;
        for (int j = 0; j < sides; j++) {
            int a = j * 2;
            idx[k++] = a;     idx[k++] = a + 1; idx[k++] = a + 2;
            idx[k++] = a + 2; idx[k++] = a + 1; idx[k++] = a + 3;
        }
        return new MeshData(pos, nor, idx);
    }
}
