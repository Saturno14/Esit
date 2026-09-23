package com.saturno14.esit.graphics.render;

import com.saturno14.esit.graphics.engine.Camera;
import com.saturno14.esit.graphics.engine.Shader;
import com.saturno14.esit.graphics.engine.Window;
import com.saturno14.esit.graphics.entity.EntityInstance;
import com.saturno14.esit.graphics.entity.EntityManagerBridge;
import src.genetics.GenomeSchema;
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

    public void run() {
        Window window = new Window(1280, 720, "Esit - Motore Grafico 3D");
        Camera camera = new Camera(window.getAspectRatio());
        Shader shader = new Shader("/shaders/terrain.vert", "/shaders/terrain.frag");

        GenomeSchema genomeSchema = GenomeSchema.loadFromClasspath("/gene_schema.json");

        SimulationWorldBridge simulationBridge = new SimulationWorldBridge();
        WorldGrid worldGrid = simulationBridge.getGrid();
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
                Matrix4f model = new Matrix4f().translation(worldPos);

                shader.setUniformMat4("uModel", model);
                shader.setUniformVec3("uBaseColor", instance.color.x, instance.color.y, instance.color.z);
                instance.getMesh().render();
            }

            shader.unbind();

            window.swapBuffersAndPollEvents();
        }

        terrainMesh.destroy();
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
