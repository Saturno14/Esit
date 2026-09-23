package com.saturno14.esit.graphics.entity;

import com.saturno14.esit.graphics.entity.skeleton.Bone;
import com.saturno14.esit.graphics.entity.skeleton.BoneRig;
import src.genetics.Genome;
import src.genetics.GenomeSchema;
import src.genetics.PhenotypeResolver;
import com.saturno14.esit.graphics.mesh.MeshData;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Applica il fenotipo (valori body.* gia' risolti da PhenotypeResolver, MAI il genoma letto
 * direttamente) al BoneRig e genera la mesh procedurale dell'entita': ogni bone diventa un
 * ellissoide allungato lungo il proprio asse, trasformato nello spazio mondo del rig e
 * unito in un'unica MeshData. Nessuna formula di genetica hardcoded qui dentro.
 *
 * La crescita con l'eta' (growthFactor) e' un meccanismo del motore grafico, non del
 * genoma: scala uniformemente tutte le lunghezze/spessori gia' risolti.
 */
public class GenomeMeshApplier {

    private static final int LAT_SEGMENTS = 6;
    private static final int LON_SEGMENTS = 8;

    private static final int MATURITY_AGE = 200;
    private static final float BABY_SCALE = 0.35f;

    public static MeshData generateBody(Genome genome, GenomeSchema schema, int age) {
        float growth = growthFactor(age);

        BoneRig rig = BoneRig.buildHumanoid();
        applyPhenotype(rig, genome, schema, growth);

        List<Float> positions = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        for (Bone bone : rig.getBones()) {
            if (bone.length <= 0f) continue;
            appendBoneEllipsoid(bone, positions, normals, indices);
        }

        return new MeshData(toFloatArray(positions), toFloatArray(normals), toIntArray(indices));
    }

    private static void applyPhenotype(BoneRig rig, Genome genome, GenomeSchema schema, float growth) {
        double torso = PhenotypeResolver.resolve("body.torso_scale", genome, schema);
        double neck = PhenotypeResolver.resolve("body.neck_length", genome, schema);
        double head = PhenotypeResolver.resolve("body.head_scale", genome, schema);
        double limbLength = PhenotypeResolver.resolve("body.limb_length", genome, schema);
        double limbThickness = PhenotypeResolver.resolve("body.limb_thickness", genome, schema);
        double hand = PhenotypeResolver.resolve("body.hand_length", genome, schema);
        double finger = PhenotypeResolver.resolve("body.finger_length", genome, schema);
        double foot = PhenotypeResolver.resolve("body.foot_length", genome, schema);

        rig.setBoneLength("pelvis", (float) torso * 0.15f * growth);
        rig.setBoneRadius("pelvis", (float) limbThickness * 1.3f * growth);

        rig.setBoneLength("torso", (float) torso * growth);
        rig.setBoneRadius("torso", (float) limbThickness * 1.3f * growth);

        rig.setBoneLength("neck", (float) neck * growth);
        rig.setBoneRadius("neck", (float) limbThickness * 0.6f * growth);

        rig.setBoneLength("head", (float) head * growth);
        rig.setBoneRadius("head", (float) head * 0.5f * growth);

        for (String side : new String[]{"L", "R"}) {
            rig.setBoneLength("arm_upper_" + side, (float) limbLength * 0.55f * growth);
            rig.setBoneRadius("arm_upper_" + side, (float) limbThickness * growth);
            rig.setBoneLength("arm_lower_" + side, (float) limbLength * 0.45f * growth);
            rig.setBoneRadius("arm_lower_" + side, (float) limbThickness * 0.85f * growth);
            rig.setBoneLength("hand_" + side, (float) hand * growth);
            rig.setBoneRadius("hand_" + side, (float) limbThickness * 0.6f * growth);
            rig.setBoneLength("finger_" + side, (float) finger * growth);
            rig.setBoneRadius("finger_" + side, (float) limbThickness * 0.35f * growth);

            rig.setBoneLength("leg_upper_" + side, (float) limbLength * 0.55f * growth);
            rig.setBoneRadius("leg_upper_" + side, (float) limbThickness * 1.2f * growth);
            rig.setBoneLength("leg_lower_" + side, (float) limbLength * 0.45f * growth);
            rig.setBoneRadius("leg_lower_" + side, (float) limbThickness * growth);
            rig.setBoneLength("foot_" + side, (float) foot * growth);
            rig.setBoneRadius("foot_" + side, (float) limbThickness * 0.7f * growth);
        }

        rig.rebuild();
    }

    /** 0 alla nascita, 1 a maturita': meccanismo del motore, indipendente dal genoma. */
    public static float growthFactor(int age) {
        float t = Math.min(1f, Math.max(0f, age / (float) MATURITY_AGE));
        return BABY_SCALE + (1f - BABY_SCALE) * t;
    }

    public static Vector3f colorFor(Genome genome, GenomeSchema schema, int sex) {
        double hue = PhenotypeResolver.resolve("body.skin_hue", genome, schema);
        Vector3f color = hueToRgb((float) hue);
        if (sex == 1) {
            color.mul(1.0f, 0.85f, 1.0f);
        }
        return color;
    }

    private static Vector3f hueToRgb(float hue) {
        float h = hue * 6f;
        float x = 1f - Math.abs((h % 2f) - 1f);
        float r, g, b;
        if (h < 1) { r = 1; g = x; b = 0; }
        else if (h < 2) { r = x; g = 1; b = 0; }
        else if (h < 3) { r = 0; g = 1; b = x; }
        else if (h < 4) { r = 0; g = x; b = 1; }
        else if (h < 5) { r = x; g = 0; b = 1; }
        else { r = 1; g = 0; b = x; }

        float sat = 0.6f;
        return new Vector3f(1 - sat + sat * r, 1 - sat + sat * g, 1 - sat + sat * b);
    }

    private static void appendBoneEllipsoid(Bone bone, List<Float> positions, List<Float> normals,
                                             List<Integer> indices) {
        float radius = Math.max(bone.radius, 0.01f);
        float halfLength = Math.max(bone.length, 0.01f) * 0.5f;

        int indexOffset = positions.size() / 3;
        Matrix4f world = bone.getWorldTransform();

        for (int lat = 0; lat <= LAT_SEGMENTS; lat++) {
            float theta = (float) Math.PI * lat / LAT_SEGMENTS;
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);

            for (int lon = 0; lon <= LON_SEGMENTS; lon++) {
                float phi = (float) (2 * Math.PI * lon / LON_SEGMENTS);
                float sinPhi = (float) Math.sin(phi);
                float cosPhi = (float) Math.cos(phi);

                float ux = sinTheta * cosPhi;
                float uy = cosTheta;
                float uz = sinTheta * sinPhi;

                // Ellissoide locale centrato a meta' lunghezza del bone, allungato sull'asse Y locale
                Vector3f local = new Vector3f(ux * radius, uy * halfLength + halfLength, uz * radius);
                Vector3f worldPos = world.transformPosition(new Vector3f(local));

                Vector3f localNormal = new Vector3f(ux / radius, uy / halfLength, uz / radius);
                if (localNormal.lengthSquared() < 1e-8f) {
                    localNormal.set(0, 1, 0);
                }
                localNormal.normalize();
                Vector3f worldNormal = world.transformDirection(new Vector3f(localNormal)).normalize();

                positions.add(worldPos.x); positions.add(worldPos.y); positions.add(worldPos.z);
                normals.add(worldNormal.x); normals.add(worldNormal.y); normals.add(worldNormal.z);
            }
        }

        int columns = LON_SEGMENTS + 1;
        for (int lat = 0; lat < LAT_SEGMENTS; lat++) {
            for (int lon = 0; lon < LON_SEGMENTS; lon++) {
                int a = indexOffset + lat * columns + lon;
                int b = a + columns;
                int c = a + 1;
                int d = b + 1;
                indices.add(a); indices.add(b); indices.add(c);
                indices.add(c); indices.add(b); indices.add(d);
            }
        }
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }
}
