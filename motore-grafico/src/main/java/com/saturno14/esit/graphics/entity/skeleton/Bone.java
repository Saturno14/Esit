package com.saturno14.esit.graphics.entity.skeleton;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Nodo di una gerarchia di bone dinamica. "restDirection" e' la direzione (in spazio del
 * RIG, cioe' assoluta, NON relativa al genitore) lungo cui il bone si estende a riposo;
 * "attachFraction" indica a quale frazione della lunghezza del genitore si attacca l'origine
 * di questo bone (1 = alla punta, 0 = alla base — utile per gambe/braccia che partono dalla
 * stessa origine del bacino invece che dalla sua punta). length/radius vengono impostati ad
 * ogni rebuild da GenomeMeshApplier a partire dai valori fenotipici risolti dal genoma.
 *
 * Nota: le direzioni sono assolute di proposito. Se fossero relative all'asse del genitore,
 * un avambraccio "verso il basso" rispetto a un braccio gia' rivolto verso il basso
 * tornerebbe indietro (forma a V al gomito/ginocchio).
 */
public class Bone {

    public final String name;
    public final Bone parent;
    public final List<Bone> children = new ArrayList<>();
    public final Vector3f restDirection;
    public final float attachFraction;

    public float length = 0.1f;
    public float radius = 0.05f;

    private final Vector3f worldPosition = new Vector3f();
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
     * Da chiamare a partire dalla radice: ricalcola la trasformazione mondiale dell'intero
     * sottoalbero. L'origine di ogni bone sta lungo la direzione (assoluta) del genitore, a
     * attachFraction * lunghezza del genitore; il bone poi si orienta sulla propria direzione.
     */
    public void rebuildTransforms() {
        if (parent == null) {
            worldPosition.zero();
        } else {
            parent.restDirection.mul(parent.length * attachFraction, worldPosition).add(parent.worldPosition);
        }

        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), restDirection);
        worldTransform.identity().translate(worldPosition).rotate(rotation);

        for (Bone child : children) {
            child.rebuildTransforms();
        }
    }

    public Matrix4f getWorldTransform() {
        return worldTransform;
    }
}
