package com.saturno14.esit.graphics.mesh;

/**
 * Dati grezzi di una mesh pronti per essere caricati su GPU: posizioni, normali e indici.
 * Prodotta sia da SurfaceNetsMesher (terreno) sia da EntityMeshGenerator (entita').
 */
public class MeshData {
    public final float[] positions; // x,y,z ripetuti
    public final float[] normals;   // nx,ny,nz ripetuti
    public final int[] indices;

    public MeshData(float[] positions, float[] normals, int[] indices) {
        this.positions = positions;
        this.normals = normals;
        this.indices = indices;
    }
}
