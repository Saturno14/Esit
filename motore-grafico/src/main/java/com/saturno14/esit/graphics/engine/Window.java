package com.saturno14.esit.graphics.engine;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

/**
 * Apre la finestra GLFW e crea il contesto OpenGL. Espone anche lo stato base della
 * tastiera/mouse, cosi' Renderer puo' leggerlo ad ogni frame.
 */
public class Window {

    private long handle;
    private int width;
    private int height;

    private double lastMouseX;
    private double lastMouseY;
    private double mouseDeltaX;
    private double mouseDeltaY;
    private boolean firstMouseMove = true;

    public Window(int width, int height, String title) {
        this.width = width;
        this.height = height;

        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Impossibile inizializzare GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        handle = glfwCreateWindow(width, height, title, 0, 0);
        if (handle == 0) {
            throw new RuntimeException("Impossibile creare la finestra GLFW");
        }

        glfwSetFramebufferSizeCallback(handle, (win, w, h) -> {
            this.width = w;
            this.height = h;
            glViewport(0, 0, w, h);
        });

        glfwSetKeyCallback(handle, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
                glfwSetWindowShouldClose(win, true);
            }
        });

        glfwSetCursorPosCallback(handle, (win, xpos, ypos) -> {
            if (firstMouseMove) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouseMove = false;
            }
            mouseDeltaX += xpos - lastMouseX;
            mouseDeltaY += ypos - lastMouseY;
            lastMouseX = xpos;
            lastMouseY = ypos;
        });

        glfwMakeContextCurrent(handle);
        glfwSwapInterval(1);
        glfwShowWindow(handle);

        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glClearColor(0.53f, 0.72f, 0.86f, 1.0f);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    public void swapBuffersAndPollEvents() {
        glfwSwapBuffers(handle);
        glfwPollEvents();
    }

    public void clear() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    public boolean isKeyPressed(int glfwKeyCode) {
        return glfwGetKey(handle, glfwKeyCode) == GLFW_PRESS;
    }

    public double[] consumeMouseDelta() {
        double[] delta = {mouseDeltaX, mouseDeltaY};
        mouseDeltaX = 0;
        mouseDeltaY = 0;
        return delta;
    }

    public float getAspectRatio() {
        return (float) width / (float) height;
    }

    public void destroy() {
        Callbacks.glfwFreeCallbacks(handle);
        glfwDestroyWindow(handle);
        glfwTerminate();
    }
}
