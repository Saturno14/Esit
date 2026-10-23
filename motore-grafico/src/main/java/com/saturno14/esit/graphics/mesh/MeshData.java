package com.saturno14.esit.graphics.mesh;

/**
 * Dati grezzi di una mesh pronti per essere caricati su GPU: posizioni, normali, indici e,
 * opzionalmente, coordinate UV (per le mesh texturate come le mele).
 * Prodotta sia da SurfaceNetsMesher (terreno) sia da GenomeMeshApplier (entita').
 */
public class MeshData {
    public final float[] positions; // x,y,z ripetuti
    public final float[] normals;   // nx,ny,nz ripetuti
    public final int[] indices;
    public final float[] uvs;       // u,v ripetuti; null se la mesh non e' texturata
    /**
     * Pesi dei materiali, quattro per vertice (vedi world.VoxelMaterial); null se la mesh non ha
     * materiali. Servono al terreno: un vertice sul confine tra erba e sabbia e' mezzo e mezzo, e
     * lo shader sfuma invece di tagliare lungo i bordi delle celle.
     */
    public final float[] materials;
    /** Occlusione ambientale, una per vertice: 1 = cielo aperto, verso 0 = incassato. */
    public final float[] ao;

    public MeshData(float[] positions, float[] normals, int[] indices) {
        this(positions, normals, indices, null);
    }

    public MeshData(float[] positions, float[] normals, int[] indices, float[] uvs) {
        this(positions, normals, indices, uvs, null, null);
    }

    public MeshData(float[] positions, float[] normals, int[] indices, float[] uvs,
                    float[] materials, float[] ao) {
        this.positions = positions;
        this.normals = normals;
        this.indices = indices;
        this.uvs = uvs;
        this.materials = materials;
        this.ao = ao;
    }
}
