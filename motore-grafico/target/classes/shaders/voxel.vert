#version 330 core

// Terreno a voxel con superficie liscia: la mesh porta normale dal gradiente, pesi dei materiali
// e occlusione ambientale per vertice (vedi mesh/VoxelMesher).
layout (location = 0) in vec3 inPosition;
layout (location = 1) in vec3 inNormal;
layout (location = 3) in vec4 inMaterials;
layout (location = 4) in float inAo;

uniform mat4 uView;
uniform mat4 uProjection;

out vec3 fragWorldPos;
out vec3 fragNormal;
out vec4 fragMaterials;
out float fragAo;

void main() {
    // le posizioni della mesh sono gia' in spazio mondo: nessuna matrice modello
    fragWorldPos = inPosition;
    fragNormal = inNormal;
    fragMaterials = inMaterials;
    fragAo = inAo;
    gl_Position = uProjection * uView * vec4(inPosition, 1.0);
}
