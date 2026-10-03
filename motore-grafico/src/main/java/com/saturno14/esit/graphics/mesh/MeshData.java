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

    public MeshData(float[] positions, float[] normals, int[] indices) {
        this(positions, normals, indices, null);
    }

    public MeshData(float[] positions, float[] normals, int[] indices, float[] uvs) {
        this.positions = positions;
        this.normals = normals;
        this.indices = indices;
        this.uvs = uvs;
    }
}
