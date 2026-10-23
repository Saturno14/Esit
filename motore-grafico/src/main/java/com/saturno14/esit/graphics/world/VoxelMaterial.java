package com.saturno14.esit.graphics.world;

/**
 * I materiali del terreno. L'indice e' anche lo strato nell'array di texture (vedi
 * render.MaterialTextures) e la posizione nel vettore dei pesi di ogni vertice: un vertice puo'
 * essere mezzo erba e mezzo sabbia, ed e' cosi' che i confini tra materiali vengono sfumati
 * invece di tagliati.
 *
 * Quattro e non piu': sono quanti ne stanno in un vec4 di pesi, cioe' quanti se ne possono
 * mescolare su un singolo vertice senza complicare il formato della mesh. Con la sfumatura, in
 * pratica, bastano.
 */
public final class VoxelMaterial {

    public static final int ROCK = 0;
    public static final int DIRT = 1;
    public static final int GRASS = 2;
    public static final int SAND = 3;
    public static final int COUNT = 4;

    /** Colore di base di ogni materiale: la texture lo modula, non lo sostituisce. */
    public static final float[][] COLOR = {
            {0.52f, 0.51f, 0.49f},   // roccia: grigio con un filo di caldo
            {0.42f, 0.31f, 0.21f},   // terra
            {0.33f, 0.52f, 0.24f},   // erba
            {0.76f, 0.69f, 0.49f},   // sabbia
    };

    private VoxelMaterial() {
    }
}
