package com.saturno14.esit.graphics.flora;

import com.saturno14.esit.graphics.mesh.MeshData;

/**
 * Le due forme di cui e' fatto un albero: un tronco e una chioma.
 *
 * ENTRAMBE SONO UNITARIE. Il tronco e' un cilindro di raggio 1 e altezza 1 col piede nell'origine;
 * la chioma e' una sfera di raggio 1 centrata nell'origine. Le dimensioni vere di ogni albero
 * (altezza, raggio della chioma) arrivano dalla matrice di modello al momento del disegno.
 *
 * PERCHE' UNITARIE E NON UNA MESH PER ALBERO. Gli alberi nascono, crescono di qualche millimetro
 * per tick e muoiono: una mesh per albero vorrebbe dire ricostruire e ricaricare geometria in
 * continuazione per una forma che non cambia mai, cambia solo di scala. Due mesh caricate una
 * volta e riusate con matrici diverse costano un upload in tutto.
 */
public final class TreeMeshes {

    /** Facce intorno al tronco: otto bastano, un tronco non e' il soggetto della scena. */
    private static final int TRUNK_SIDES = 8;
    /** Suddivisioni della chioma: paralleli e meridiani. */
    private static final int CROWN_LAT = 7;
    private static final int CROWN_LON = 10;

    private TreeMeshes() {
    }

    /**
     * Cilindro con il piede sul piano y = 0 e la cima a y = 1, raggio 1.
     *
     * Le normali laterali puntano in fuori ORIZZONTALMENTE (non verso l'asse inclinato) perche'
     * un cilindro ha i fianchi verticali: cosi' la luce gira attorno al tronco come deve, e il
     * lato in ombra resta in ombra anche guardandolo dall'alto.
     */
    public static MeshData trunk() {
        // un lato in piu' dei fianchi: il vertice di chiusura e' DOPPIO, nella stessa posizione
        // ma con u = 1 invece di u = 0. Senza il doppione l'ultima faccia dovrebbe interpolare la
        // texture da u = 0.875 a u = 0, cioe' tutta la texture al rovescio in una faccia sola.
        int ringVerts = TRUNK_SIDES + 1;
        int rings = 2;                               // basso e alto: il tronco non si deforma
        int verts = ringVerts * rings + 2;           // + i due centri dei tappi
        float[] pos = new float[verts * 3];
        float[] nor = new float[verts * 3];
        float[] uv = new float[verts * 2];
        int p = 0;
        int q = 0;

        for (int ring = 0; ring < rings; ring++) {
            float y = ring;
            for (int s = 0; s <= TRUNK_SIDES; s++) {
                double a = 2 * Math.PI * s / TRUNK_SIDES;
                float cx = (float) Math.cos(a);
                float cz = (float) Math.sin(a);
                pos[p] = cx;
                pos[p + 1] = y;
                pos[p + 2] = cz;
                nor[p] = cx;
                nor[p + 1] = 0;
                nor[p + 2] = cz;
                p += 3;
                // u gira attorno al tronco, v sale. La v vera la scala lo shader con l'altezza
                // dell'albero (uVScale), altrimenti un albero di sei metri avrebbe la stessa
                // corteccia stirata di uno di due.
                uv[q] = s / (float) TRUNK_SIDES;
                uv[q + 1] = y;
                q += 2;
            }
        }
        int bottomCentre = ringVerts * rings;
        int topCentre = bottomCentre + 1;
        pos[bottomCentre * 3 + 1] = 0;
        nor[bottomCentre * 3 + 1] = -1;
        uv[bottomCentre * 2] = 0.5f;
        uv[bottomCentre * 2 + 1] = 0.5f;
        pos[topCentre * 3 + 1] = 1;
        nor[topCentre * 3 + 1] = 1;
        uv[topCentre * 2] = 0.5f;
        uv[topCentre * 2 + 1] = 0.5f;

        int[] idx = new int[TRUNK_SIDES * 12];
        int i = 0;
        for (int s = 0; s < TRUNK_SIDES; s++) {
            int b0 = s;
            int b1 = s + 1;
            int t0 = ringVerts + s;
            int t1 = ringVerts + s + 1;
            idx[i++] = b0;
            idx[i++] = t0;
            idx[i++] = t1;
            idx[i++] = b0;
            idx[i++] = t1;
            idx[i++] = b1;
            // tappi: quello in basso resta sotto terra, quello in alto lo copre la chioma, ma
            // chiuderli costa niente e un tronco aperto si vede da dentro
            idx[i++] = bottomCentre;
            idx[i++] = b1;
            idx[i++] = b0;
            idx[i++] = topCentre;
            idx[i++] = t0;
            idx[i++] = t1;
        }
        return new MeshData(pos, nor, idx, uv);
    }

    /** Sfera di raggio 1 centrata nell'origine, a poligoni grossi (e' una chioma, non un pianeta). */
    public static MeshData crown() {
        int verts = (CROWN_LAT + 1) * (CROWN_LON + 1);
        float[] pos = new float[verts * 3];
        float[] nor = new float[verts * 3];
        float[] uv = new float[verts * 2];
        int p = 0;
        int q = 0;
        for (int la = 0; la <= CROWN_LAT; la++) {
            double phi = Math.PI * la / CROWN_LAT;
            float y = (float) Math.cos(phi);
            float r = (float) Math.sin(phi);
            for (int lo = 0; lo <= CROWN_LON; lo++) {
                double th = 2 * Math.PI * lo / CROWN_LON;
                float x = (float) (r * Math.cos(th));
                float z = (float) (r * Math.sin(th));
                pos[p] = x;
                pos[p + 1] = y;
                pos[p + 2] = z;
                // su una sfera di raggio 1 centrata nell'origine la normale e' la posizione
                nor[p] = x;
                nor[p + 1] = y;
                nor[p + 2] = z;
                p += 3;
                // due giri di texture attorno e due dall'alto in basso: il fogliame deve avere
                // grana piu' fine della chioma, altrimenti i grumi diventano continenti
                uv[q] = 2f * lo / CROWN_LON;
                uv[q + 1] = 2f * la / CROWN_LAT;
                q += 2;
            }
        }
        int[] idx = new int[CROWN_LAT * CROWN_LON * 6];
        int i = 0;
        for (int la = 0; la < CROWN_LAT; la++) {
            for (int lo = 0; lo < CROWN_LON; lo++) {
                int a = la * (CROWN_LON + 1) + lo;
                int b = a + 1;
                int c = a + (CROWN_LON + 1);
                int d = c + 1;
                idx[i++] = a;
                idx[i++] = c;
                idx[i++] = d;
                idx[i++] = a;
                idx[i++] = d;
                idx[i++] = b;
            }
        }
        return new MeshData(pos, nor, idx, uv);
    }
}
