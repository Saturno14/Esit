package com.saturno14.esit.graphics.render;

import com.saturno14.esit.graphics.engine.Camera;
import com.saturno14.esit.graphics.engine.Shader;
import com.saturno14.esit.graphics.engine.Window;
import com.saturno14.esit.graphics.entity.EntityInstance;
import com.saturno14.esit.graphics.entity.EntityManagerBridge;
import com.saturno14.esit.graphics.entity.PoseRenderer;
import com.saturno14.esit.graphics.apple.AppleMesh;
import com.saturno14.esit.graphics.apple.AppleTexture;
import com.saturno14.esit.graphics.flora.FloraField;
import com.saturno14.esit.graphics.flora.TreeMeshes;
import com.saturno14.esit.graphics.flora.TreeTexture;
import com.saturno14.esit.graphics.world.WaterSurface;
import src.genetics.GenomeSchema;
import src.entity;
import com.saturno14.esit.graphics.mesh.Mesh;
import com.saturno14.esit.graphics.mesh.Primitives;
import com.saturno14.esit.graphics.world.GridToWorld;
import com.saturno14.esit.graphics.world.SimulationWorldBridge;
import com.saturno14.esit.graphics.world.WorldGrid;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

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
        // due shader: uno a colore pieno per entita' e cadaveri, uno per il terreno a voxel
        // (texture triplanari, materiali mescolati, occlusione ambientale)
        Shader shader = new Shader("/shaders/solid.vert", "/shaders/solid.frag");
        Shader voxelShader = new Shader("/shaders/voxel.vert", "/shaders/voxel.frag");
        Shader appleShader = new Shader("/shaders/apple.vert", "/shaders/apple.frag");
        AppleTexture appleTexture = new AppleTexture();
        Mesh appleMesh = new Mesh(AppleMesh.create());
        // tronco e chioma sono due forme unitarie riusate con una matrice per albero: vedi TreeMeshes
        Mesh trunkMesh = new Mesh(TreeMeshes.trunk());
        Mesh crownMesh = new Mesh(TreeMeshes.crown());
        Shader treeShader = new Shader("/shaders/tree.vert", "/shaders/tree.frag");
        TreeTexture treeTexture = new TreeTexture();

        // L'acqua: nella simulazione esiste da sempre, a schermo non c'era. Si disegna per ultima,
        // in trasparenza, quindi ha bisogno del suo shader e della sua mesh (vedi WaterSurface).
        Shader waterShader = new Shader("/shaders/water.vert", "/shaders/water.frag");
        WaterTexture waterTexture = new WaterTexture();
        WaterSurface waterSurface = new WaterSurface();
        long startNanos = System.nanoTime();

        // Stesso schema usato dalle entita' della simulazione: un'unica fonte di verita'
        GenomeSchema genomeSchema = entity.getSchema();

        SimulationWorldBridge simulationBridge = new SimulationWorldBridge();
        WorldGrid worldGrid = simulationBridge.getGrid();

        // La simulazione NON parte piu' in automatico: si apre la finestra di controllo (liste,
        // grafici e comandi), da cui l'utente la avvia con "newstart" (mondo nuovo) o "load" (da
        // un salvataggio). Il mondo (terreno) e' comunque gia' pronto: lo crea
        // SimulationWorldBridge, quindi si vede subito il terreno anche prima dell'avvio.
        //
        // La GUI gira nello STESSO processo (prima era una finestra cmd separata che parlava via
        // socket): legge Entity_manager e Genealogy direttamente, e il clic destro qui sotto le
        // passa l'entita' selezionata nella scena.
        src.gui.Gui.start();

        // matrici riusate: dentro al ciclo di disegno si allocherebbero migliaia di Matrix4f al
        // secondo, e il garbage collector si vede come scatti nell'immagine
        Matrix4f treeModel = new Matrix4f();
        Matrix4f appleModel = new Matrix4f();
        EntityManagerBridge entityBridge = new EntityManagerBridge(genomeSchema);
        PoseRenderer poseRenderer = new PoseRenderer();

        /*
         * L'INDICATORE DELL'ENTITA' SELEZIONATA: un'asta sottile sopra la testa con una pallina
         * in cima, che pulsa. Due forme unitarie riusate con una matrice, come gli alberi: non
         * costa niente, perche' di selezionata ce n'e' una sola.
         *
         * PERCHE' SOPRA LA TESTA e non un contorno attorno al corpo. Un contorno vero (stencil,
         * secondo passaggio con il corpo gonfiato) vorrebbe dire ridisegnare tutta la posa una
         * seconda volta e un altro shader; l'asta si vede anche quando l'entita' e' in mezzo ad
         * altre, anche di spalle, e anche se e' piccola, che e' tutto quello che serve per
         * rispondere a "quale ho preso?".
         */
        Mesh indicatoreAsta = new Mesh(Primitives.cylinder(8));
        Mesh indicatorePallina = new Mesh(Primitives.sphere(10, 14));
        Matrix4f indicatoreModel = new Matrix4f();

        TerrainRenderer terrainRenderer = new TerrainRenderer(worldGrid);

        // la luce non e' piu' fissa: direzione e forza vengono dall'ora del giorno
        float[] skyDay = {0.55f, 0.68f, 0.85f};
        float[] skyNight = {0.05f, 0.07f, 0.14f};
        float[] skyColor = new float[3];

        Vector3f entityBase = new Vector3f();
        Matrix4f corpseTransform = new Matrix4f();

        // Interpolazione dell'andatura tra due tick: la simulazione avanza a scatti (un tick vale
        // mezzo secondo di mondo, in un tempo reale che dipende dalla velocita' scelta), il
        // rendering gira a ogni frame. Misurando quanto e' durato l'ultimo tick si sa a che punto
        // del tick si e', e si fa avanzare il ciclo delle gambe di quella frazione.
        long lastSeenTick = src.Simulation.getTick();
        double lastTickRealTime = glfwGetTime();
        double observedTickPeriod = src.Simulation.TICK_MS / 1000.0;

        double lastFrameTime = glfwGetTime();

        while (!window.shouldClose()) {
            double now = glfwGetTime();
            float deltaTime = (float) (now - lastFrameTime);
            lastFrameTime = now;

            handleInput(window, camera, deltaTime);
            provaSelezione(window, camera, entityBridge);

            long simTick = src.Simulation.getTick();
            if (simTick != lastSeenTick) {
                double elapsed = now - lastTickRealTime;
                if (elapsed > 1e-4) {
                    // media mobile: un tick singolo piu' lento non fa sobbalzare l'animazione
                    observedTickPeriod = 0.7 * observedTickPeriod + 0.3 * elapsed / Math.max(1, simTick - lastSeenTick);
                }
                lastSeenTick = simTick;
                lastTickRealTime = now;
            }
            double tickFraction = Math.min(1.0, (now - lastTickRealTime) / Math.max(1e-4, observedTickPeriod));
            double extraSeconds = tickFraction * (src.Simulation.TICK_MS / 1000.0);

            simulationBridge.syncFromSimulation();
            entityBridge.sync();

            terrainRenderer.update();

            // ora del giorno: sole che gira, cielo che si spegne, luce che cala
            double daylight = src.DayNight.daylight();
            float[] light = src.DayNight.lightDirection();
            float sunStrength = (float) (0.10 + 0.90 * daylight);
            for (int c = 0; c < 3; c++) {
                skyColor[c] = (float) (skyNight[c] + (skyDay[c] - skyNight[c]) * daylight);
            }
            window.setClearColor(skyColor[0], skyColor[1], skyColor[2]);

            window.clear();

            // Terreno: le posizioni della mesh sono gia' in spazio mondo, niente matrice modello
            voxelShader.bind();
            voxelShader.setUniformMat4("uView", camera.getViewMatrix());
            voxelShader.setUniformMat4("uProjection", camera.getProjectionMatrix());
            terrainRenderer.render(voxelShader, light, skyColor, sunStrength);

            shader.bind();
            shader.setUniformMat4("uView", camera.getViewMatrix());
            shader.setUniformMat4("uProjection", camera.getProjectionMatrix());
            shader.setUniformVec3("uLightDirection", light[0], light[1], light[2]);
            shader.setUniformFloat("uLightStrength", sunStrength);

            // Entita': si chiede a ciascuna la posa dei suoi segmenti e la si disegna pezzo per
            // pezzo, con il proprio colore genetico e lo stesso shader del terreno. La posizione
            // e' quella continua in metri, non la cella: cosi' il movimento e' fluido e si vede
            // l'andatura che la fisica ha misurato per quel corpo.
            for (EntityInstance instance : entityBridge.getInstances()) {
                int[] pos = instance.getPosition();
                float groundY = GridToWorld.toWorld(0, pos[1], 0).y - SURFACE_OFFSET;
                instance.advanceRenderPosition(deltaTime);
                entityBase.set(instance.renderX(), groundY, instance.renderZ());

                float r = instance.color.x, g = instance.color.y, b = instance.color.z;
                Matrix4f extra = null;
                if (instance.isDead()) {
                    // Cadavere: sdraiato sul terreno e scurito (grigio spento). La rotazione si
                    // applica attorno al punto in cui tocca terra, non attorno all'origine del mondo.
                    extra = corpseTransform.identity()
                            .translate(entityBase.x, entityBase.y + 0.18f, entityBase.z)
                            .rotateX((float) Math.toRadians(-90))
                            .translate(-entityBase.x, -entityBase.y, -entityBase.z);
                    r = r * 0.25f + 0.18f;
                    g = g * 0.25f + 0.18f;
                    b = b * 0.25f + 0.18f;
                }

                // L'entita' selezionata si schiarisce: l'asta dice QUALE, il colore dice che e'
                // quella anche guardando solo il corpo
                boolean selezionata = instance.entityId == src.gui.Gui.entitaSelezionata();
                if (selezionata) {
                    r = Math.min(1f, r * 0.55f + 0.45f);
                    g = Math.min(1f, g * 0.55f + 0.45f);
                    b = Math.min(1f, b * 0.55f + 0.25f);
                }

                shader.setUniformVec3("uBaseColor", r, g, b);
                poseRenderer.draw(shader, instance.pose(extraSeconds), entityBase, extra);

                if (selezionata) {
                    disegnaIndicatore(shader, indicatoreModel, indicatoreAsta, indicatorePallina,
                            entityBase, altezzaDi(instance), (float) now);
                }
            }

            shader.unbind();

            // Alberi: tronco con la corteccia, chioma col fogliame (TreeTexture). La tinta varia
            // un po' da albero a albero (dalla posizione) perche' un bosco di cloni dello stesso
            // verde si legge come una superficie sola invece che come tanti alberi.
            treeShader.bind();
            treeShader.setUniformMat4("uView", camera.getViewMatrix());
            treeShader.setUniformMat4("uProjection", camera.getProjectionMatrix());
            treeShader.setUniformVec3("uLightDirection", light[0], light[1], light[2]);
            treeShader.setUniformFloat("uLightStrength", sunStrength);
            treeShader.setUniformVec3("uSkyColor", skyColor[0], skyColor[1], skyColor[2]);
            treeTexture.bind(0);
            treeShader.setUniformInt("uTexture", 0);
            for (FloraField.Tree tree : FloraField.trees()) {
                float footY = tree.baseY() - SURFACE_OFFSET;
                // il raggio lo decide Flora, perche' il tronco e' anche un ostacolo fisico:
                // disegnarlo di una misura e urtarlo di un'altra sarebbe un mondo che mente
                float trunkRadius = tree.trunkRadius();
                float height = Math.max(0.05f, tree.height());
                treeModel.identity()
                        .translate(tree.x(), footY, tree.z())
                        .scale(trunkRadius, height, trunkRadius);
                treeShader.setUniformInt("uLayer", TreeTexture.LAYER_BARK);
                // due giri di corteccia attorno al tronco, e in altezza una ripetizione ogni
                // mezzo metro: cosi' la grana non dipende da quanto e' alto l'albero
                treeShader.setUniformVec2("uUvScale", 2f, height * 2f);
                treeShader.setUniformVec3("uTint", 1f, 0.95f, 0.92f);
                treeShader.setUniformMat4("uModel", treeModel);
                trunkMesh.render();

                float crown = tree.crownRadius();
                float centreY = footY + tree.height() - 0.42f * crown;
                float tint = ((int) (tree.x() * 7 + tree.z() * 13) % 7) / 24f;
                float young = 1f - tree.maturity();
                treeModel.identity()
                        .translate(tree.x(), centreY, tree.z())
                        .scale(crown * 0.80f, crown * 0.70f, crown * 0.80f);
                treeShader.setUniformInt("uLayer", TreeTexture.LAYER_LEAVES);
                treeShader.setUniformVec2("uUvScale", crown * 0.8f, crown * 0.8f);
                // un albero giovane e' piu' chiaro e giallino di uno adulto
                treeShader.setUniformVec3("uTint",
                        0.85f + tint + 0.30f * young,
                        1.00f + tint * 0.4f + 0.20f * young,
                        0.80f + tint * 0.2f);
                treeShader.setUniformMat4("uModel", treeModel);
                crownMesh.render();
            }
            treeShader.unbind();

            // Mele: shader texturato dedicato; ognuna con una rotazione diversa attorno a Y
            // (derivata dalla posizione) cosi' non sembrano tutte copie identiche.
            appleShader.bind();
            appleShader.setUniformMat4("uView", camera.getViewMatrix());
            appleShader.setUniformMat4("uProjection", camera.getProjectionMatrix());
            appleShader.setUniformVec3("uLightDirection", light[0], light[1], light[2]);
            Vector3f camPos = camera.getPosition();
            appleShader.setUniformVec3("uCameraPos", camPos.x, camPos.y, camPos.z);
            appleTexture.bind(0);
            appleShader.setUniformInt("uTexture", 0);
            appleShader.setUniformFloat("uLightStrength", sunStrength);
            for (FloraField.Apple apple : FloraField.apples()) {
                // una mela a terra poggia sulla superficie (mezza cella sotto la cella d'aria);
                // una appesa sta dove dice la chioma, quindi la sua quota non va corretta
                float y = apple.onTree() ? apple.y() : apple.y() - SURFACE_OFFSET;
                float angle = ((int) (apple.x() * 31 + apple.z() * 17) % 628) / 100f;
                appleModel.identity()
                        .translate(apple.x(), y, apple.z())
                        .rotateY(angle)
                        .scale(apple.size());
                appleShader.setUniformFloat("uRipeness", apple.ripeness());
                appleShader.setUniformMat4("uModel", appleModel);
                appleMesh.render();
            }
            appleShader.unbind();

            /*
             * L'ACQUA PER ULTIMA, IN TRASPARENZA. Un oggetto trasparente si mescola con quello
             * che c'e' GIA' nel buffer dei colori, quindi tutto cio' che gli sta dietro deve
             * essere stato disegnato prima - se l'acqua andasse per prima, il fondale disegnato
             * dopo la coprirebbe e non si vedrebbe attraverso.
             *
             * E con la scrittura della profondita' SPENTA (glDepthMask(false)): la profondita' si
             * continua a leggere, cosi' una riva piu' alta copre l'acqua che le sta dietro, ma non
             * si scrive, altrimenti due celle d'acqua adiacente si nasconderebbero a vicenda e la
             * superficie si bucherebbe a seconda dell'ordine in cui escono dalla mesh.
             */
            waterSurface.update();
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDepthMask(false);
            waterShader.bind();
            waterShader.setUniformMat4("uView", camera.getViewMatrix());
            waterShader.setUniformMat4("uProjection", camera.getProjectionMatrix());
            waterShader.setUniformVec3("uLightDirection", light[0], light[1], light[2]);
            waterShader.setUniformVec3("uSkyColor", skyColor[0], skyColor[1], skyColor[2]);
            waterShader.setUniformFloat("uLightStrength", sunStrength);
            waterShader.setUniformFloat("uTime", (System.nanoTime() - startNanos) / 1e9f);
            Vector3f waterCam = camera.getPosition();
            waterShader.setUniformVec3("uCameraPos", waterCam.x, waterCam.y, waterCam.z);
            waterTexture.bind(0);
            waterShader.setUniformInt("uTexture", 0);
            waterSurface.render();
            waterShader.unbind();
            glDepthMask(true);
            glDisable(GL_BLEND);

            window.swapBuffersAndPollEvents();
        }

        terrainRenderer.destroy();
        waterSurface.destroy();
        waterTexture.destroy();
        waterShader.destroy();
        trunkMesh.destroy();
        crownMesh.destroy();
        treeTexture.destroy();
        treeShader.destroy();
        appleMesh.destroy();
        appleTexture.destroy();
        appleShader.destroy();
        entityBridge.destroyAll();
        indicatoreAsta.destroy();
        indicatorePallina.destroy();
        poseRenderer.destroy();
        shader.destroy();
        voxelShader.destroy();
        window.destroy();
    }

    /*
     * SELEZIONE CON IL TASTO DESTRO: dal pixel all'entita'.
     *
     * Il pixel sotto il puntatore non e' un punto del mondo ma una RETTA: tutti i punti che la
     * proiezione schiaccia in quel pixel. Si ricostruisce quella retta invertendo
     * proiezione*vista (unprojectRay), poi si cerca la prima entita' che la retta incontra.
     *
     * Ogni entita' si approssima con una SFERA invece che col suo scheletro. Intersecare la retta
     * con le decine di segmenti della posa darebbe la risposta esatta, ma per selezionare non
     * serve essere esatti: serve prendere quella che si stava guardando, e una sfera alta quanto
     * il corpo sbaglia solo se due entita' sono quasi sovrapposte - caso in cui si prende quella
     * piu' vicina alla camera, che e' anche quella che si vede.
     */
    private void provaSelezione(Window window, Camera camera, EntityManagerBridge bridge) {
        double[] clic = window.consumePickRequest();
        if (clic == null) {
            return;
        }
        Matrix4f pv = new Matrix4f(camera.getProjectionMatrix()).mul(camera.getViewMatrix());
        int[] viewport = {0, 0, window.getWidth(), window.getHeight()};
        Vector3f origine = new Vector3f();
        Vector3f direzione = new Vector3f();
        // GLFW conta le righe dall'alto, OpenGL dal basso: la y va ribaltata
        pv.unprojectRay((float) clic[0], window.getHeight() - (float) clic[1],
                viewport, origine, direzione);
        direzione.normalize();

        float piuVicina = Float.MAX_VALUE;
        int scelta = -1;
        Vector3f centro = new Vector3f();
        for (EntityInstance instance : bridge.getInstances()) {
            int[] pos = instance.getPosition();
            float groundY = GridToWorld.toWorld(0, pos[1], 0).y - SURFACE_OFFSET;
            float altezza = altezzaDi(instance);
            centro.set(instance.renderX(), groundY + altezza * 0.5f, instance.renderZ());
            // generoso di proposito: si clicca su un'entita' alta mezzo metro, vista da lontano,
            // che nel frattempo cammina. Meglio prendere quella accanto che non prendere niente
            float raggio = Math.max(0.6f, altezza * 0.7f);
            float t = distanzaSfera(origine, direzione, centro, raggio);
            if (t >= 0 && t < piuVicina) {
                piuVicina = t;
                scelta = instance.entityId;
            }
        }

        if (scelta >= 0) {
            src.gui.Gui.selezionaEntita(scelta);
        } else {
            src.gui.Gui.messaggio("Nessuna entita' sotto il puntatore.");
        }
    }

    /**
     * L'asta e la pallina sopra la testa dell'entita' selezionata.
     *
     * Pulsa (sale e scende di una decina di centimetri) perche' un oggetto fermo in mezzo a un
     * mondo che si muove si legge come parte della scena: e' il movimento che lo fa leggere come
     * un segno messo sopra la scena.
     */
    private static void disegnaIndicatore(Shader shader, Matrix4f model, Mesh asta, Mesh pallina,
                                          Vector3f base, float altezza, float tempo) {
        float pulsazione = (float) (0.10 * Math.sin(tempo * 3.0));
        float piede = base.y + altezza + 0.18f + pulsazione;
        float lunghezza = 0.45f;

        shader.setUniformVec3("uBaseColor", 1.0f, 0.86f, 0.15f);
        model.identity().translate(base.x, piede, base.z).scale(0.035f, lunghezza, 0.035f);
        shader.setUniformMat4("uModel", model);
        asta.render();

        model.identity().translate(base.x, piede + lunghezza, base.z).scale(0.11f);
        shader.setUniformMat4("uModel", model);
        pallina.render();
    }

    private static float altezzaDi(EntityInstance instance) {
        src.entity source = instance.getSource();
        if (source == null) {
            return 1f;
        }
        src.body.BodyTraits traits = source.getTraits();
        return traits == null ? 1f : (float) Math.max(0.3, traits.height());
    }

    /**
     * Dove la retta incontra la sfera, o -1 se non la incontra.
     *
     * Si scartano le intersezioni dietro all'origine (t negativo): un'entita' alle spalle della
     * camera sta pure sulla retta, ma non e' quella su cui si e' cliccato.
     */
    private static float distanzaSfera(Vector3f origine, Vector3f direzione,
                                       Vector3f centro, float raggio) {
        float ox = origine.x - centro.x;
        float oy = origine.y - centro.y;
        float oz = origine.z - centro.z;
        float b = ox * direzione.x + oy * direzione.y + oz * direzione.z;
        float c = ox * ox + oy * oy + oz * oz - raggio * raggio;
        float disc = b * b - c;
        if (disc < 0) {
            return -1;
        }
        float radice = (float) Math.sqrt(disc);
        float t1 = -b - radice;
        float t2 = -b + radice;
        if (t1 >= 0) {
            return t1;
        }
        return t2 >= 0 ? t2 : -1;
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
