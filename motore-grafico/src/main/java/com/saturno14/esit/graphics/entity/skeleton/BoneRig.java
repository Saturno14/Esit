package com.saturno14.esit.graphics.entity.skeleton;

import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gerarchia di bone di un'entita' umanoide semplificata: la struttura (nomi, genitori,
 * direzioni di riposo) e' fissa; lunghezze e spessori vengono impostati da
 * GenomeMeshApplier a partire dai valori body.* risolti da PhenotypeResolver.
 *
 * bacino (pelvis)
 *  |- torso -> neck -> head
 *  |- arm_upper_{L,R} -> arm_lower_{L,R} -> hand_{L,R} -> finger_{L,R}   (attaccate a torso)
 *  |- leg_upper_{L,R} -> leg_lower_{L,R} -> foot_{L,R}                  (attaccate a pelvis)
 */
public class BoneRig {

    private final Bone root;
    private final Map<String, Bone> bones = new LinkedHashMap<>();

    private BoneRig(Bone root) {
        this.root = root;
    }

    public static BoneRig buildHumanoid() {
        Bone pelvis = new Bone("pelvis", null, new Vector3f(0, 1, 0));

        Bone torso = new Bone("torso", pelvis, new Vector3f(0, 1, 0), 1f);
        Bone neck = new Bone("neck", torso, new Vector3f(0, 1, 0), 1f);
        new Bone("head", neck, new Vector3f(0, 1, 0), 1f);

        for (float side : new float[]{-1f, 1f}) {
            String tag = side < 0 ? "L" : "R";

            Bone armUpper = new Bone("arm_upper_" + tag, torso, new Vector3f(side, -0.1f, 0), 1f);
            Bone armLower = new Bone("arm_lower_" + tag, armUpper, new Vector3f(side * 0.3f, -1f, 0), 1f);
            Bone hand = new Bone("hand_" + tag, armLower, new Vector3f(side * 0.15f, -1f, 0), 1f);
            new Bone("finger_" + tag, hand, new Vector3f(side * 0.1f, -1f, 0), 1f);

            Bone legUpper = new Bone("leg_upper_" + tag, pelvis, new Vector3f(side * 0.4f, -1f, 0), 0f);
            Bone legLower = new Bone("leg_lower_" + tag, legUpper, new Vector3f(0, -1f, 0), 1f);
            new Bone("foot_" + tag, legLower, new Vector3f(0, -0.3f, 1f), 1f);
        }

        BoneRig rig = new BoneRig(pelvis);
        rig.registerRecursive(pelvis);
        return rig;
    }

    private void registerRecursive(Bone bone) {
        bones.put(bone.name, bone);
        for (Bone child : bone.children) {
            registerRecursive(child);
        }
    }

    public void setBoneLength(String name, float length) {
        Bone b = bones.get(name);
        if (b != null) {
            b.length = length;
        }
    }

    public void setBoneRadius(String name, float radius) {
        Bone b = bones.get(name);
        if (b != null) {
            b.radius = radius;
        }
    }

    public void rebuild() {
        root.rebuildTransforms();
    }

    public Iterable<Bone> getBones() {
        return bones.values();
    }
}
