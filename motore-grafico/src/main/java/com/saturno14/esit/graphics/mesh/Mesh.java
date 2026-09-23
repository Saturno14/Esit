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
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
    }
}
