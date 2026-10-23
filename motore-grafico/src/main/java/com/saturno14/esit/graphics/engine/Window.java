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
    private boolean leftButtonDown = false;

    /** Clic in attesa di essere servito: la posizione in pixel e il fatto che ci sia. */
    private double pickX;
    private double pickY;
    private boolean pickPending = false;
    /** Pixel percorsi col tasto sinistro premuto: sotto la soglia e' un clic, sopra un trascinamento. */
    private double dragDistance = 0;
    private static final double CLICK_SLOP = 4.0;

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
            // La visuale si muove solo trascinando con il tasto sinistro premuto.
            // lastMouse viene aggiornato sempre, cosi' alla pressione del tasto non c'e' nessun salto.
            if (leftButtonDown) {
                mouseDeltaX += xpos - lastMouseX;
                mouseDeltaY += ypos - lastMouseY;
                // quanto si e' spostato il puntatore da quando si e' premuto: distingue il clic
                // dal trascinamento (vedi la callback dei tasti)
                dragDistance += Math.abs(xpos - lastMouseX) + Math.abs(ypos - lastMouseY);
            }
            lastMouseX = xpos;
            lastMouseY = ypos;
        });

        glfwSetMouseButtonCallback(handle, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                leftButtonDown = (action == GLFW_PRESS);
                if (leftButtonDown) {
                    dragDistance = 0;
                } else {
                    /*
                     * CLIC SINISTRO = SELEZIONA, TRASCINAMENTO SINISTRO = GIRA LA VISUALE.
                     *
                     * Sono lo stesso tasto perche' e' quello che si usa d'istinto per indicare
                     * una cosa a schermo, e un tasto che a volte non fa niente si legge come un
                     * programma rotto. Si distinguono al RILASCIO, guardando quanto si e' mosso
                     * il puntatore mentre era premuto: sotto la soglia era un clic (si seleziona
                     * cio' che sta sotto), sopra era un trascinamento (la visuale e' gia' girata,
                     * e non si seleziona niente). La soglia non e' zero perche' tenendo premuto
                     * il mouse si sposta sempre di un pixel o due.
                     */
                    if (dragDistance <= CLICK_SLOP) {
                        pickX = lastMouseX;
                        pickY = lastMouseY;
                        pickPending = true;
                    }
                    // A fine trascinamento scarta eventuale delta residuo
                    mouseDeltaX = 0;
                    mouseDeltaY = 0;
                }
            }
            /*
             * Anche il tasto destro seleziona, e senza l'ambiguita' del trascinamento: resta
             * come alternativa sicura quando si sta girando la visuale di continuo.
             *
             * La richiesta si mette da parte invece di essere servita qui: questa callback gira
             * dentro glfwPollEvents, dove non c'e' niente delle matrici della camera di questo
             * frame. Il Renderer la raccoglie subito dopo, quando le ha (Renderer.provaSelezione).
             */
            if (button == GLFW_MOUSE_BUTTON_RIGHT && action == GLFW_PRESS) {
                pickX = lastMouseX;
                pickY = lastMouseY;
                pickPending = true;
            }
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

    /** Colore del cielo: cambia con l'ora del giorno (vedi src.DayNight). */
    public void setClearColor(float r, float g, float b) {
        glClearColor(r, g, b, 1.0f);
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

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * La posizione dell'ultimo clic destro, o null se non ce n'e' uno da servire.
     * "consume" perche' la richiesta si legge una volta sola: un clic seleziona un'entita', non
     * una per ogni frame finche' non se ne fa un altro.
     */
    public double[] consumePickRequest() {
        if (!pickPending) {
            return null;
        }
        pickPending = false;
        return new double[]{pickX, pickY};
    }

    public void destroy() {
        Callbacks.glfwFreeCallbacks(handle);
        glfwDestroyWindow(handle);
        glfwTerminate();
    }
}
