package com.saturno14.esit.graphics.render;

import com.saturno14.esit.graphics.engine.Shader;
import com.saturno14.esit.graphics.mesh.Mesh;
import com.saturno14.esit.graphics.mesh.MeshData;
import com.saturno14.esit.graphics.mesh.VoxelMesher;
import com.saturno14.esit.graphics.world.WorldGrid;

import java.util.HashMap;
import java.util.Map;

/**
 * Disegna il terreno, una mesh per chunk.
 *
 * PERCHE' A CHUNK. Prima il terreno era UNA mesh: qualunque modifica - una sola cella scavata -
 * voleva dire ricostruirla tutta e rimandarla alla scheda grafica. A lato 50 sono qualche
 * centinaio di migliaia di celle da riesaminare per un cucchiaio di terra. Con i chunk si rifa'
 * solo il cubo di 16 celle di lato che e' cambiato (e i vicini, se la cella stava sul bordo),
 * quindi il costo di una modifica non dipende piu' da quanto e' grande il mondo. E' la differenza
 * tra un terreno che si guarda e un terreno che si scava.
 *
 * COSTO PER FRAME. Si rifa' al massimo {@link #MAX_REBUILDS_PER_FRAME} chunk per frame: se una
 * modifica grossa ne sporca molti, la ricostruzione si distribuisce su qualche frame invece di
 * far scattare l'immagine. I chunk rimasti restano in coda nella griglia.
 */
public class TerrainRenderer {

    /** Quanti chunk si possono ricostruire in un frame prima di rimandare il resto. */
    private static final int MAX_REBUILDS_PER_FRAME = 8;
    /** Ripetizioni di texture per metro: piu' alto = grana piu' fine. */
    private static final float TEXTURE_SCALE = 0.35f;

    private final WorldGrid grid;
    private final MaterialTextures textures;
    private final Map<Long, Mesh> meshes = new HashMap<>();
    private final java.util.ArrayDeque<Long> pending = new java.util.ArrayDeque<>();

    public TerrainRenderer(WorldGrid grid) {
        this.grid = grid;
        this.textures = new MaterialTextures();
    }

    /** Ricostruisce i chunk cambiati, pochi per frame. Da chiamare prima di disegnare. */
    public void update() {
        for (long k : grid.takeDirtyChunks()) {
            if (!pending.contains(k)) {
                pending.add(k);
            }
        }
        for (int i = 0; i < MAX_REBUILDS_PER_FRAME && !pending.isEmpty(); i++) {
            long k = pending.poll();
            rebuild(k);
        }
    }

    private void rebuild(long key) {
        MeshData data = VoxelMesher.generate(grid,
                WorldGrid.keyX(key), WorldGrid.keyY(key), WorldGrid.keyZ(key));
        Mesh mesh = meshes.get(key);
        if (data == null) {
            // chunk diventato tutto pieno o tutto vuoto: non c'e' superficie da disegnare
            if (mesh != null) {
                mesh.destroy();
                meshes.remove(key);
            }
            return;
        }
        if (mesh == null) {
            meshes.put(key, new Mesh(data));
        } else {
            mesh.upload(data);
        }
    }

    public void render(Shader shader, float[] lightDirection, float[] skyColor, float sunStrength) {
        shader.bind();
        shader.setUniformVec3("uLightDirection", lightDirection[0], lightDirection[1], lightDirection[2]);
        shader.setUniformVec3("uSkyColor", skyColor[0], skyColor[1], skyColor[2]);
        shader.setUniformFloat("uSunStrength", sunStrength);
        shader.setUniformFloat("uTexScale", TEXTURE_SCALE);
        textures.bind(0);
        shader.setUniformInt("uMaterials", 0);
        for (Mesh mesh : meshes.values()) {
            mesh.render();
        }
        shader.unbind();
    }

    /** Quanti chunk hanno superficie e quanti sono in attesa di essere rifatti (diagnostica). */
    public String stats() {
        return meshes.size() + " chunk disegnati, " + pending.size() + " da rifare";
    }

    public void destroy() {
        for (Mesh mesh : meshes.values()) {
            mesh.destroy();
        }
        meshes.clear();
        textures.destroy();
    }
}
