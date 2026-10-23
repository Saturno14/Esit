#version 330 core

layout (location = 0) in vec3 inPosition;
layout (location = 1) in vec3 inNormal;
layout (location = 2) in vec2 inUv;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;
/**
 * Scala delle UV. Le due mesh dell'albero sono unitarie e ogni albero le scala con la sua matrice
 * di modello: senza questa correzione la texture si stirerebbe con l'albero, e un albero di sei
 * metri avrebbe la corteccia larga il triplo di uno di due. Qui si moltiplica la UV per la
 * dimensione VERA, cosi' la grana della corteccia e del fogliame resta la stessa su tutti.
 */
uniform vec2 uUvScale;

out vec3 fragNormal;
out vec3 fragWorldPos;
out vec2 fragUv;

void main() {
    vec4 worldPos = uModel * vec4(inPosition, 1.0);
    fragWorldPos = worldPos.xyz;
    // la matrice di modello dell'albero ha scale non uniformi (tronco: sottile e alto), quindi la
    // normale va trasformata con l'inversa trasposta, non con la matrice stessa
    fragNormal = mat3(transpose(inverse(uModel))) * inNormal;
    fragUv = inUv * uUvScale;
    gl_Position = uProjection * uView * worldPos;
}
