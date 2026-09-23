package com.saturno14.esit.graphics.mesh;

import com.saturno14.esit.graphics.world.WorldGrid;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Genera una mesh continua e "morbida" da un campo scalare 3D (WorldGrid), usando
 * l'algoritmo Surface Nets.
 *
 * 1. Per ogni "cubetto" di 8 celle adiacenti del grid, se la superficie lo attraversa
 *    (angoli sia pieni che vuoti), posiziona un vertice nel punto medio delle intersezioni
 *    superficie/spigoli.
 * 2. Per ogni SPIGOLO del grid (tra due punti griglia adiacenti lungo X, Y o Z) attraversato
 *    dalla superficie, collega con un quad i (fino a) 4 cubetti che condividono quello spigolo.
 *
 * I cubetti esistono per indici 0..dim-2 su ogni asse. Uno spigolo lungo l'asse X e'
 * identificato da un punto griglia (x,y,z) con x in 0..dim-2 (deve poter avanzare di 1),
 * MENTRE y e z devono coprire l'intero range di punto griglia 0..dim-1 (compreso il bordo) —
 * altrimenti si perdono i lati e il fondo della mesh.
 *
 * NOTA: MeshData e' ora una classe condivisa (vedi mesh/MeshData.java), usata anche da
 * EntityMeshGenerator per le mesh procedurali delle entita'.
 */
public class SurfaceNetsMesher {

    private static final float ISO_LEVEL = 0.5f;


    public static MeshData generate(WorldGrid grid) {
        int dim = grid.getDimension();

        // Estendo i cicli di 1 cella oltre i limiti della griglia su ogni lato (-1 e dim).
        // getDensity() ritorna 0 ("vuoto") per qualsiasi coordinata fuori range, quindi questo
        // fa si' che i confini reali del volume (fondo, lati, eventuale tetto se il solido
        // arriva fino in cima) vengano trattati come cambi di segno e quindi chiusi con una
        // superficie, invece di restare aperti. Senza questo padding si vede solo la superficie
        // interna (es. dove Terra incontra Aria), non i bordi del volume stesso.
        int cubeMin = -1;
        int cubeMax = dim - 1; // cubo di indice i copre i punti [i, i+1]

        Map<Long, Integer> vertexIndexByCell = new HashMap<>();
        List<Float> positions = new ArrayList<>();
        List<Float> normalsAccumList = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        for (int x = cubeMin; x <= cubeMax; x++) {
            for (int y = cubeMin; y <= cubeMax; y++) {
                for (int z = cubeMin; z <= cubeMax; z++) {
                    Vector3f vertex = computeCellVertex(grid, x, y, z);
                    if (vertex != null) {
                        int index = positions.size() / 3;
                        positions.add(vertex.x);
                        positions.add(vertex.y);
                        positions.add(vertex.z);
                        normalsAccumList.add(0f);
                        normalsAccumList.add(0f);
                        normalsAccumList.add(0f);
                        vertexIndexByCell.put(cellKey(x, y, z), index);
                    }
                }
            }
        }

        float[] normalAccum = new float[normalsAccumList.size()];

        int pointMin = -1;
        int pointMax = dim; // punto griglia esteso: -1..dim (dim+2 valori)

        for (int x = cubeMin; x <= cubeMax; x++) {
            for (int y = pointMin; y <= pointMax; y++) {
                for (int z = pointMin; z <= pointMax; z++) {
                    emitQuadForEdge(grid, vertexIndexByCell, indices, positions, normalAccum,
                            x, y, z, 1, 0, 0);
                }
            }
        }

        for (int x = pointMin; x <= pointMax; x++) {
            for (int y = cubeMin; y <= cubeMax; y++) {
                for (int z = pointMin; z <= pointMax; z++) {
                    emitQuadForEdge(grid, vertexIndexByCell, indices, positions, normalAccum,
                            x, y, z, 0, 1, 0);
                }
            }
        }

        for (int x = pointMin; x <= pointMax; x++) {
            for (int y = pointMin; y <= pointMax; y++) {
                for (int z = cubeMin; z <= cubeMax; z++) {
                    emitQuadForEdge(grid, vertexIndexByCell, indices, positions, normalAccum,
                            x, y, z, 0, 0, 1);
                }
            }
        }

        int vertexCount = normalAccum.length / 3;
        List<Float> finalNormals = new ArrayList<>(normalAccum.length);
        for (int i = 0; i < vertexCount; i++) {
            float nx = normalAccum[i * 3];
            float ny = normalAccum[i * 3 + 1];
            float nz = normalAccum[i * 3 + 2];
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-6f) {
                finalNormals.add(nx / len);
                finalNormals.add(ny / len);
                finalNormals.add(nz / len);
            } else {
                finalNormals.add(0f);
                finalNormals.add(1f);
                finalNormals.add(0f);
            }
        }

        return new MeshData(toFloatArray(positions), toFloatArray(finalNormals), toIntArray(indices));
    }

    private static Vector3f computeCellVertex(WorldGrid grid, int x, int y, int z) {
        float[] corner = new float[8];
        corner[0] = grid.getDensity(x, y, z);
        corner[1] = grid.getDensity(x + 1, y, z);
        corner[2] = grid.getDensity(x + 1, y, z + 1);
        corner[3] = grid.getDensity(x, y, z + 1);
        corner[4] = grid.getDensity(x, y + 1, z);
        corner[5] = grid.getDensity(x + 1, y + 1, z);
        corner[6] = grid.getDensity(x + 1, y + 1, z + 1);
        corner[7] = grid.getDensity(x, y + 1, z + 1);

        boolean anyInside = false;
        boolean anyOutside = false;
        for (float c : corner) {
            if (c >= ISO_LEVEL) anyInside = true;
            else anyOutside = true;
        }
        if (!anyInside || !anyOutside) {
            return null;
        }

        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        float[][] cornerOffset = {
                {0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1},
                {0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}
        };

        float sumX = 0, sumY = 0, sumZ = 0;
        int crossings = 0;

        for (int[] edge : edges) {
            float dA = corner[edge[0]];
            float dB = corner[edge[1]];
            boolean signChange = (dA >= ISO_LEVEL) != (dB >= ISO_LEVEL);
            if (!signChange) continue;

            float t = clamp01((ISO_LEVEL - dA) / (dB - dA));

            float[] a = cornerOffset[edge[0]];
            float[] b = cornerOffset[edge[1]];
            sumX += a[0] + t * (b[0] - a[0]);
            sumY += a[1] + t * (b[1] - a[1]);
            sumZ += a[2] + t * (b[2] - a[2]);
            crossings++;
        }

        if (crossings == 0) return null;

        return new Vector3f(x + sumX / crossings, y + sumY / crossings, z + sumZ / crossings);
    }

    private static void emitQuadForEdge(WorldGrid grid, Map<Long, Integer> vertexIndexByCell,
                                         List<Integer> indices, List<Float> positions, float[] normalAccum,
                                         int x, int y, int z, int dx, int dy, int dz) {
        float d0 = grid.getDensity(x, y, z);
        float d1 = grid.getDensity(x + dx, y + dy, z + dz);
        boolean signChange = (d0 >= ISO_LEVEL) != (d1 >= ISO_LEVEL);
        if (!signChange) return;

        int[][] offsets;
        if (dx == 1) {
            offsets = new int[][]{{0, 0, 0}, {0, -1, 0}, {0, -1, -1}, {0, 0, -1}};
        } else if (dy == 1) {
            offsets = new int[][]{{0, 0, 0}, {-1, 0, 0}, {-1, 0, -1}, {0, 0, -1}};
        } else {
            offsets = new int[][]{{0, 0, 0}, {-1, 0, 0}, {-1, -1, 0}, {0, -1, 0}};
        }

        Integer[] quad = new Integer[4];
        for (int i = 0; i < 4; i++) {
            quad[i] = vertexIndexByCell.get(cellKey(x + offsets[i][0], y + offsets[i][1], z + offsets[i][2]));
        }
        for (Integer q : quad) {
            if (q == null) return;
        }

        boolean flip = d0 >= ISO_LEVEL;
        int a = quad[0], b = quad[1], c = quad[2], d = quad[3];
        if (flip) {
            addTriangle(indices, positions, normalAccum, a, b, c);
            addTriangle(indices, positions, normalAccum, a, c, d);
        } else {
            addTriangle(indices, positions, normalAccum, a, c, b);
            addTriangle(indices, positions, normalAccum, a, d, c);
        }
    }

    private static void addTriangle(List<Integer> indices, List<Float> positions, float[] normalAccum,
                                     int i0, int i1, int i2) {
        indices.add(i0);
        indices.add(i1);
        indices.add(i2);

        Vector3f p0 = getPos(positions, i0);
        Vector3f p1 = getPos(positions, i1);
        Vector3f p2 = getPos(positions, i2);
        Vector3f edge1 = new Vector3f(p1).sub(p0);
        Vector3f edge2 = new Vector3f(p2).sub(p0);
        Vector3f faceNormal = new Vector3f(edge1).cross(edge2);

        accumulateNormal(normalAccum, i0, faceNormal);
        accumulateNormal(normalAccum, i1, faceNormal);
        accumulateNormal(normalAccum, i2, faceNormal);
    }

    private static Vector3f getPos(List<Float> positions, int index) {
        return new Vector3f(positions.get(index * 3), positions.get(index * 3 + 1), positions.get(index * 3 + 2));
    }

    private static void accumulateNormal(float[] normalAccum, int index, Vector3f n) {
        normalAccum[index * 3] += n.x;
        normalAccum[index * 3 + 1] += n.y;
        normalAccum[index * 3 + 2] += n.z;
    }

    private static long cellKey(int x, int y, int z) {
        // +1 di bias: il range usato ora e' -1..dim, questo lo riporta a 0..dim+1,
        // sempre non negativo, cosi' l'incastro nei bit non ha ambiguita' con numeri negativi.
        long bx = x + 1;
        long by = y + 1;
        long bz = z + 1;
        return (bx << 40) ^ (by << 20) ^ bz;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
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
