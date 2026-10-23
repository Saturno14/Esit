package com.saturno14.esit.graphics.mesh;

import static org.lwjgl.opengl.GL33.*;

/**
 * Incapsula VAO/VBO/EBO per una mesh: la carica una volta sulla GPU e la puo' aggiornare
 * quando il WorldGrid cambia (es. un'entita' scava/costruisce e va rigenerata la superficie).
 */
public class Mesh {

    private int vao;
    private int positionVbo;
    private int normalVbo;
    private int uvVbo;
    private int materialVbo;
    private int aoVbo;
    private int ebo;
    private int indexCount;

    public Mesh(MeshData data) {
        upload(data);
    }

    public void upload(MeshData data) {
        if (vao == 0) {
            vao = glGenVertexArrays();
        }
        glBindVertexArray(vao);

        if (positionVbo == 0) positionVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, positionVbo);
        glBufferData(GL_ARRAY_BUFFER, data.positions, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(0);

        if (normalVbo == 0) normalVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, normalVbo);
        glBufferData(GL_ARRAY_BUFFER, data.normals, GL_DYNAMIC_DRAW);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0);
        glEnableVertexAttribArray(1);

        if (data.uvs != null) {
            if (uvVbo == 0) uvVbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, uvVbo);
            glBufferData(GL_ARRAY_BUFFER, data.uvs, GL_DYNAMIC_DRAW);
            glVertexAttribPointer(2, 2, GL_FLOAT, false, 0, 0);
            glEnableVertexAttribArray(2);
        } else {
            glDisableVertexAttribArray(2);
        }

        // attributi del terreno: pesi dei materiali (3) e occlusione ambientale (4)
        if (data.materials != null) {
            if (materialVbo == 0) materialVbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, materialVbo);
            glBufferData(GL_ARRAY_BUFFER, data.materials, GL_DYNAMIC_DRAW);
            glVertexAttribPointer(3, 4, GL_FLOAT, false, 0, 0);
            glEnableVertexAttribArray(3);
        } else {
            glDisableVertexAttribArray(3);
        }

        if (data.ao != null) {
            if (aoVbo == 0) aoVbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, aoVbo);
            glBufferData(GL_ARRAY_BUFFER, data.ao, GL_DYNAMIC_DRAW);
            glVertexAttribPointer(4, 1, GL_FLOAT, false, 0, 0);
            glEnableVertexAttribArray(4);
        } else {
            glDisableVertexAttribArray(4);
        }

        if (ebo == 0) ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, data.indices, GL_DYNAMIC_DRAW);

        indexCount = data.indices.length;

        glBindVertexArray(0);
    }

    public void render() {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void destroy() {
        glDeleteBuffers(positionVbo);
        glDeleteBuffers(normalVbo);
        if (uvVbo != 0) glDeleteBuffers(uvVbo);
        if (materialVbo != 0) glDeleteBuffers(materialVbo);
        if (aoVbo != 0) glDeleteBuffers(aoVbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
    }
}
