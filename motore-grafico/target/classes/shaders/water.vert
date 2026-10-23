#version 330 core

layout (location = 0) in vec3 inPosition;
layout (location = 1) in vec3 inNormal;
layout (location = 2) in vec2 inUv;

uniform mat4 uView;
uniform mat4 uProjection;

out vec3 fragWorldPos;
out vec2 fragUv;

void main() {
    // la mesh dell'acqua e' gia' in coordinate mondo (vedi WaterSurface): niente matrice di modello
    fragWorldPos = inPosition;
    fragUv = inUv;
    gl_Position = uProjection * uView * vec4(inPosition, 1.0);
}
