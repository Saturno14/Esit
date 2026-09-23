package com.saturno14.esit.graphics.entity.skeleton;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Nodo di una gerarchia di bone dinamica. "restDirection" e' l'asse (in spazio locale del
 * genitore) lungo cui il bone si estende a riposo; "attachFraction" indica a quale
 * frazione della lunghezza del genitore si attacca l'origine di questo bone (1 = alla
 * punta, 0 = alla base — utile per gambe/braccia che partono dalla stessa origine del
 * bacino invece che dalla sua punta). length/radius vengono impostati ad ogni rebuild da
 * GenomeMeshApplier a partire dai valori fenotipici risolti dal genoma.
 */
public class Bone {

    public final String name;
    public final Bone parent;
    public final List<Bone> children = new ArrayList<>();
    public final Vector3f restDirection;
    public final float attachFraction;

    public float length = 0.1f;
    public float radius = 0.05f;

    private final Matrix4f localTransform = new Matrix4f();
    private final Matrix4f worldTransform = new Matrix4f();

    public Bone(String name, Bone parent, Vector3f restDirection) {
        this(name, parent, restDirection, 1f);
    }

    public Bone(String name, Bone parent, Vector3f restDirection, float attachFraction) {
        this.name = name;
        this.parent = parent;
        this.restDirection = new Vector3f(restDirection).normalize();
        this.attachFraction = attachFraction;
        if (parent != null) {
            parent.children.add(this);
        }
    }

    /**
     * Da chiamare a partire dalla radice: ricalcola trasformazioni locali e mondiali
     * dell'intero sottoalbero. L'offset e' sempre espresso lungo l'asse Y locale del
     * genitore (0,1,0) scalato per la sua lunghezza: e' la rotazione applicata al genitore,
     * gia' incorporata nel suo worldTransform, a orientarlo correttamente nello spazio.
     */
    public void rebuildTransforms() {
        Vector3f offset = parent != null
                ? new Vector3f(0, 1, 0).mul(parent.length * this.attachFraction)
                : new Vector3f();

        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), restDirection);
        localTransform.identity().translate(offset).rotate(rotation);

        if (parent == null) {
            worldTransform.set(localTransform);
        } else {
            parent.worldTransform.mul(localTransform, worldTransform);
        }

        for (Bone child : children) {
            child.rebuildTransforms();
        }
    }

    public Matrix4f getWorldTransform() {
        return worldTransform;
    }
}
