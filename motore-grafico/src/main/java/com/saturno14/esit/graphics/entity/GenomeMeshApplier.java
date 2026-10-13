package com.saturno14.esit.graphics.entity;

import src.genetics.Genome;
import src.genetics.GenomeSchema;
import src.genetics.PhenotypeResolver;
import com.saturno14.esit.graphics.mesh.MeshData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Genera la mesh procedurale dell'entita' dal suo corpo articolato (src.body.Automaton, costruito dal
 * piano del corpo del genoma): ogni link diventa un ellissoide allungato lungo il proprio asse, nello
 * spazio mondo, e tutto viene unito in un'unica MeshData. La struttura del corpo e' libera (umanoide,
 * serpente, arti in piu'...) e la mesh la segue; il colore dipende ancora da body.skin_hue.
 *
 * La crescita con l'eta' (growthFactor) scala uniformemente tutte le misure del corpo.
 * Bone/BoneRig (skeleton/) non sono piu' usati qui.
 */
public class GenomeMeshApplier {

    private static final int LAT_SEGMENTS = 6;
    private static final int LON_SEGMENTS = 8;

    private static final int MATURITY_AGE = 200;
    private static final float BABY_SCALE = 0.35f;

    /**
     * Mesh del corpo: un ellissoide per ogni link dell'automa dell'entita' (src.body.Automaton), costruito
     * dal suo piano del corpo. Cosi' la forma che si vede e' la stessa usata da fisica e collisioni, qualunque
     * sia la struttura (umanoide, serpente, arti in piu'...). Posa di riposo: tutti gli angoli a zero.
     */
    public static MeshData generateBody(Genome genome, GenomeSchema schema, int age) {
        src.body.Automaton body = src.body.Automaton.fromPlan(genome.bodyPlan(schema), growthFactor(age));
        src.body.BodyPose pose = body.pose(null, src.body.V3.ZERO, src.body.Rot.IDENTITY);

        List<Float> positions = new ArrayList<>();
        List<Float> normals = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        for (src.body.Link link : body.links()) {
            src.body.BodyPose.LinkPose lp = pose.get(link.name);
            src.body.V3 start = lp.origin().add(lp.rot().apply(link.start()));
            src.body.V3 dir = lp.rot().apply(link.dir);
            Matrix4f world = new Matrix4f()
                    .translate((float) start.x(), (float) start.y(), (float) start.z())
                    .rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                            new Vector3f((float) dir.x(), (float) dir.y(), (float) dir.z())));
            appendEllipsoid(world, (float) link.radius, (float) link.length, positions, normals, indices);
        }

        // Il punto piu' basso del corpo sta a y = 0, cosi' l'entita' poggia sul terreno invece di affondarci.
        float minY = Float.MAX_VALUE;
        for (int i = 1; i < positions.size(); i += 3) {
            minY = Math.min(minY, positions.get(i));
        }
        if (minY != Float.MAX_VALUE) {
            for (int i = 1; i < positions.size(); i += 3) {
                positions.set(i, positions.get(i) - minY);
            }
        }

        return new MeshData(toFloatArray(positions), toFloatArray(normals), toIntArray(indices));
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

    private static void appendEllipsoid(Matrix4f world, float boneRadius, float boneLength, List<Float> positions,
                                         List<Float> normals, List<Integer> indices) {
        float radius = Math.max(boneRadius, 0.01f);
        float halfLength = Math.max(boneLength, 0.01f) * 0.5f;

        int indexOffset = positions.size() / 3;

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
