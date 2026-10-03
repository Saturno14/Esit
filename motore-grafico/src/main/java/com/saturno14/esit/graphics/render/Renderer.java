package com.saturno14.esit.graphics.render;

import com.saturno14.esit.graphics.engine.Camera;
import com.saturno14.esit.graphics.engine.Shader;
import com.saturno14.esit.graphics.engine.Window;
import com.saturno14.esit.graphics.entity.EntityInstance;
import com.saturno14.esit.graphics.entity.EntityManagerBridge;
import com.saturno14.esit.graphics.apple.AppleField;
import com.saturno14.esit.graphics.apple.AppleMesh;
import com.saturno14.esit.graphics.apple.AppleTexture;
import com.saturno14.esit.graphics.world.ConsoleLauncher;
import src.genetics.GenomeSchema;
import src.entity;
import com.saturno14.esit.graphics.mesh.Mesh;
import com.saturno14.esit.graphics.mesh.SurfaceNetsMesher;
import com.saturno14.esit.graphics.world.GridToWorld;
import com.saturno14.esit.graphics.world.SimulationWorldBridge;
import com.saturno14.esit.graphics.world.WorldGrid;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Punto di raccolta del motore: crea finestra, camera, mondo, mesh e shader,
 * poi esegue il render loop principale.
 */
public class Renderer {

    private static final float MOVE_SPEED = 10f;
    private static final float MOUSE_SENSITIVITY = 0.1f;
    /**
     * La superficie del terreno sta a meta' tra l'ultima cella di terra e la prima d'aria
     * (Surface Nets con iso 0.5): entita' e mele stanno nella cella d'aria, quindi le si
     * abbassa di mezza cella per farle poggiare sul terreno.
     */
    private static final float SURFACE_OFFSET = 0.5f;

    public void run() {
        Window window = new Window(1280, 720, "Esit - Motore Grafico 3D");
        Camera camera = new Camera(window.getAspectRatio());
        Shader shader = new Shader("/shaders/terrain.vert", "/shaders/terrain.frag");
        Shader appleShader = new Shader("/shaders/apple.vert", "/shaders/apple.frag");
        AppleTexture appleTexture = new AppleTexture();
        Mesh appleMesh = new Mesh(AppleMesh.create());

        // Stesso schema usato dalle entita' della simulazione: un'unica fonte di verita'
        GenomeSchema genomeSchema = entity.getSchema();

        SimulationWorldBridge simulationBridge = new SimulationWorldBridge();
        WorldGrid worldGrid = simulationBridge.getGrid();

        // La simulazione NON parte piu' in automatico: si apre solo la console dei comandi,
        // da cui l'utente la avvia con "NewStart" (mondo nuovo) o "Load" (da un salvataggio).
        // Il mondo (terreno) e' comunque gia' pronto: lo crea SimulationWorldBridge, quindi si
        // vede subito il terreno anche prima che la simulazione sia avviata.
        ConsoleLauncher.start();
        EntityManagerBridge entityBridge = new EntityManagerBridge(genomeSchema);

        Mesh terrainMesh = new Mesh(SurfaceNetsMesher.generate(worldGrid));

        Vector3f lightDirection = new Vector3f(-0.4f, -1f, -0.3f).normalize();
        Vector3f terrainColor = new Vector3f(0.35f, 0.55f, 0.30f);

        double lastFrameTime = glfwGetTime();

        while (!window.shouldClose()) {
            double now = glfwGetTime();
            float deltaTime = (float) (now - lastFrameTime);
            lastFrameTime = now;

            handleInput(window, camera, deltaTime);

            simulationBridge.syncFromSimulation();
            entityBridge.sync();

            if (worldGrid.isDirty()) {
                terrainMesh.upload(SurfaceNetsMesher.generate(worldGrid));
                worldGrid.clearDirty();
            }

            window.clear();

            shader.bind();
            shader.setUniformMat4("uView", camera.getViewMatrix());
            shader.setUniformMat4("uProjection", camera.getProjectionMatrix());
            shader.setUniformVec3("uLightDirection", lightDirection.x, lightDirection.y, lightDirection.z);

            // Terreno: matrice modello identita' (le coordinate del mesh sono gia' in spazio mondo)
            shader.setUniformMat4("uModel", new Matrix4f().identity());
            shader.setUniformVec3("uBaseColor", terrainColor.x, terrainColor.y, terrainColor.z);
            terrainMesh.render();

            // Entita': una matrice modello per ciascuna (traslazione alla sua posizione nel mondo)
            // e il proprio colore genetico, riusando lo stesso shader del terreno.
            for (EntityInstance instance : entityBridge.getInstances()) {
                int[] pos = instance.getPosition();
                Vector3f worldPos = GridToWorld.toWorld(pos[0], pos[1], pos[2]);
                Matrix4f model = new Matrix4f().translation(worldPos.x, worldPos.y - SURFACE_OFFSET, worldPos.z);
                float r = instance.color.x, g = instance.color.y, b = instance.color.z;
                if (instance.isDead()) {
                    // Cadavere: sdraiato sul terreno e scurito (grigio spento)
                    model.translate(0f, 0.18f, 0f).rotateX((float) Math.toRadians(-90));
                    r = r * 0.25f + 0.18f;
                    g = g * 0.25f + 0.18f;
                    b = b * 0.25f + 0.18f;
                }

                shader.setUniformMat4("uModel", model);
                shader.setUniformVec3("uBaseColor", r, g, b);
                instance.getMesh().render();
            }

            shader.unbind();

            // Mele: shader texturato dedicato; ognuna con una rotazione diversa attorno a Y
            // (derivata dalla posizione) cosi' non sembrano tutte copie identiche.
            appleShader.bind();
            appleShader.setUniformMat4("uView", camera.getViewMatrix());
            appleShader.setUniformMat4("uProjection", camera.getProjectionMatrix());
            appleShader.setUniformVec3("uLightDirection", lightDirection.x, lightDirection.y, lightDirection.z);
            Vector3f camPos = camera.getPosition();
            appleShader.setUniformVec3("uCameraPos", camPos.x, camPos.y, camPos.z);
            appleTexture.bind(0);
            appleShader.setUniformInt("uTexture", 0);
            for (int[] apple : AppleField.scan()) {
                Vector3f p = GridToWorld.toWorld(apple[0], apple[1], apple[2]);
                float angle = ((apple[0] * 31 + apple[2] * 17) % 628) / 100f;
                Matrix4f appleModel = new Matrix4f()
                        .translation(p.x, p.y - SURFACE_OFFSET, p.z)
                        .rotateY(angle);
                appleShader.setUniformMat4("uModel", appleModel);
                appleMesh.render();
            }
            appleShader.unbind();

            window.swapBuffersAndPollEvents();
        }

        terrainMesh.destroy();
        appleMesh.destroy();
        appleTexture.destroy();
        appleShader.destroy();
        entityBridge.destroyAll();
        shader.destroy();
        window.destroy();
    }

    private void handleInput(Window window, Camera camera, float deltaTime) {
        float distance = MOVE_SPEED * deltaTime;

        Vector3f forward = camera.getForward();
        Vector3f right = camera.getRight();

        if (window.isKeyPressed(GLFW_KEY_W)) {
            camera.move(new Vector3f(forward).mul(distance));
        }
        if (window.isKeyPressed(GLFW_KEY_S)) {
            camera.move(new Vector3f(forward).mul(-distance));
        }
        if (window.isKeyPressed(GLFW_KEY_A)) {
            camera.move(new Vector3f(right).mul(-distance));
        }
        if (window.isKeyPressed(GLFW_KEY_D)) {
            camera.move(new Vector3f(right).mul(distance));
        }
        if (window.isKeyPressed(GLFW_KEY_SPACE)) {
            camera.move(new Vector3f(0, distance, 0));
        }
        if (window.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
            camera.move(new Vector3f(0, -distance, 0));
        }

        double[] mouseDelta = window.consumeMouseDelta();
        camera.rotate((float) mouseDelta[0] * MOUSE_SENSITIVITY, (float) -mouseDelta[1] * MOUSE_SENSITIVITY);

        camera.setAspectRatio(window.getAspectRatio());
    }
}
