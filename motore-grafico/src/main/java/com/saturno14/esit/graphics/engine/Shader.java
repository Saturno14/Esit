package com.saturno14.esit.graphics.engine;

import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL33.*;

/**
 * Carica, compila e linka un vertex+fragment shader in un unico shader program,
 * e offre metodi comodi per impostare gli uniform.
 */
public class Shader {

    private final int programId;

    public Shader(String vertexResourcePath, String fragmentResourcePath) {
        int vertexId = compile(readResource(vertexResourcePath), GL_VERTEX_SHADER);
        int fragmentId = compile(readResource(fragmentResourcePath), GL_FRAGMENT_SHADER);

        programId = glCreateProgram();
        glAttachShader(programId, vertexId);
        glAttachShader(programId, fragmentId);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Errore di linking shader: " + glGetProgramInfoLog(programId));
        }

        glDeleteShader(vertexId);
        glDeleteShader(fragmentId);
    }

    private int compile(String source, int type) {
        int id = glCreateShader(type);
        glShaderSource(id, source);
        glCompileShader(id);

        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            String kind = (type == GL_VERTEX_SHADER) ? "vertex" : "fragment";
            throw new RuntimeException("Errore di compilazione shader (" + kind + "): " + glGetShaderInfoLog(id));
        }
        return id;
    }

    private String readResource(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new RuntimeException("Risorsa shader non trovata sul classpath: " + resourcePath
                        + " (deve stare sotto src/main/resources)");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Errore leggendo lo shader " + resourcePath, e);
        }
    }

    public void bind() {
        glUseProgram(programId);
    }

    public void unbind() {
        glUseProgram(0);
    }

    public void setUniformMat4(String name, Matrix4f matrix) {
        int location = glGetUniformLocation(programId, name);
        float[] buffer = new float[16];
        matrix.get(buffer);
        glUniformMatrix4fv(location, false, buffer);
    }

    public void setUniformVec3(String name, float x, float y, float z) {
        int location = glGetUniformLocation(programId, name);
        glUniform3f(location, x, y, z);
    }

    public void destroy() {
        glDeleteProgram(programId);
    }
}
