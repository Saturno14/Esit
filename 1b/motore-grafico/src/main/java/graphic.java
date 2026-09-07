
import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;

public class graphic {

    private long window; // handle nativo GLFW alla finestra (un long, non un oggetto Java)

    public void run() {
        init();
        loop();

        // cleanup finestra e GLFW quando il ciclo finisce
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void init() {
        // GLFW deve essere inizializzato prima di ogni altra chiamata
        if (!glfwInit()) {
            throw new IllegalStateException("Impossibile inizializzare GLFW");
        }

        // Configura le "window hints" PRIMA di creare la finestra
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);   // resta nascosta finché non è pronta
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3); // OpenGL 3.3 core profile
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        // Crea la finestra: larghezza, altezza, titolo, monitor (null = windowed), share (null)
        window = glfwCreateWindow(1280, 720, "Esit - Mondo 3D", 0, 0);
        if (window == 0) {
            throw new RuntimeException("Impossibile creare la finestra GLFW");
        }

        // Rende questo contesto OpenGL "corrente" sul thread attuale
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // v-sync: 1 = sincronizza col refresh rate del monitor

        glfwShowWindow(window);

        // Fondamentale: crea le "capabilities" OpenGL per questo contesto,
        // altrimenti ogni chiamata gl* successiva fallisce
        GL.createCapabilities();

        glClearColor(0.1f, 0.1f, 0.15f, 1.0f); // colore di sfondo (grigio-blu scuro)
    }

    private void loop() {
        // finché l'utente non chiude la finestra
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // qui, più avanti, andrà il draw call del mondo

            glfwSwapBuffers(window);  // mostra il frame appena disegnato
            glfwPollEvents();          // processa input/eventi finestra
        }
    }

    public static void main(String[] args) {
        new graphic().run();
    }
}