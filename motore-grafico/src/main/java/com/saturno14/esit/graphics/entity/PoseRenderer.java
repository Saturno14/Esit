package com.saturno14.esit.graphics.entity;

import com.saturno14.esit.graphics.engine.Shader;
import com.saturno14.esit.graphics.mesh.Mesh;
import com.saturno14.esit.graphics.mesh.Primitives;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import src.body.BodyPose;

/**
 * Disegna un corpo articolato segmento per segmento, nella posa che gli ha dato la simulazione
 * (src.entity.poseLocal): ogni segmento e' un cilindro tra i suoi due centri piu' una sfera
 * all'estremita'. Niente mesh per entita' e niente rigenerazioni: due sole forme sulla GPU e una
 * matrice per pezzo.
 *
 * E' questo che rende visibile l'andatura: gli angoli dei giunti arrivano dall'oscillatore
 * dell'entita', quindi una gamba lunga fa un passo ampio e lento e si VEDE sullo schermo.
 */
public class PoseRenderer {

    private static final int SPHERE_LAT = 8;
    private static final int SPHERE_LON = 12;
    private static final int CYLINDER_SIDES = 10;
    /** Sotto questa lunghezza il segmento e' solo una pallina: il cilindro non si vedrebbe. */
    private static final double MIN_SEGMENT = 1e-4;

    private static final Vector3f UP = new Vector3f(0, 1, 0);

    private final Mesh sphere = new Mesh(Primitives.sphere(SPHERE_LAT, SPHERE_LON));
    private final Mesh cylinder = new Mesh(Primitives.cylinder(CYLINDER_SIDES));

    // riusati a ogni pezzo: il render loop non deve allocare
    private final Matrix4f model = new Matrix4f();
    private final Vector3f a = new Vector3f();
    private final Vector3f b = new Vector3f();
    private final Vector3f dir = new Vector3f();
    private final Vector3f scale = new Vector3f();
    private final Quaternionf rot = new Quaternionf();

    /**
     * @param base traslazione da sommare alla posa (la posa e' nel sistema locale dell'entita':
     *             origine sotto i piedi)
     * @param extra rotazione/traslazione aggiuntiva, es. un cadavere sdraiato; puo' essere null
     */
    public void draw(Shader shader, BodyPose pose, Vector3f base, Matrix4f extra) {
        boolean first = true;
        for (BodyPose.LinkPose link : pose.links()) {
            a.set((float) link.coreA().x(), (float) link.coreA().y(), (float) link.coreA().z());
            b.set((float) link.coreB().x(), (float) link.coreB().y(), (float) link.coreB().z());
            float r = (float) link.radius();

            // sfera all'estremita' lontana: coprendo coreB di ogni segmento si chiudono anche i
            // giunti, perche' il coreA di un figlio cade dove sta il coreB del padre
            model.identity();
            if (extra != null) {
                model.mul(extra);
            }
            model.translate(base.x + b.x, base.y + b.y, base.z + b.z).scale(r);
            shader.setUniformMat4("uModel", model);
            sphere.render();

            if (first) {
                // il primo segmento e' la radice: il suo coreA non e' coperto da nessun padre,
                // e il cilindro non ha coperchi, quindi li' serve una sfera in piu'
                first = false;
                model.identity();
                if (extra != null) {
                    model.mul(extra);
                }
                model.translate(base.x + a.x, base.y + a.y, base.z + a.z).scale(r);
                shader.setUniformMat4("uModel", model);
                sphere.render();
            }

            dir.set(b).sub(a);
            float length = dir.length();
            if (length < MIN_SEGMENT) {
                continue;
            }
            dir.div(length);
            rot.identity().rotationTo(UP, dir);
            scale.set(r, length, r);
            model.identity();
            if (extra != null) {
                model.mul(extra);
            }
            model.translate(base.x + a.x, base.y + a.y, base.z + a.z)
                    .rotate(rot)
                    .scale(scale);
            shader.setUniformMat4("uModel", model);
            cylinder.render();
        }
    }

    public void destroy() {
        sphere.destroy();
        cylinder.destroy();
    }
}
