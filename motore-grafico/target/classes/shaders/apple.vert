#version 330 core

layout (location = 0) in vec3 inPosition;
layout (location = 1) in vec3 inNormal;
layout (location = 2) in vec2 inUv;

uniform mat4 uModel;
uniform mat4 uView;
uniform mat4 uProjection;

out vec3 fragNormal;
out vec3 fragWorldPos;
out vec2 fragUv;

void main() {
    vec4 worldPos = uModel * vec4(inPosition, 1.0);
    fragWorldPos = worldPos.xyz;
    fragNormal = mat3(uModel) * inNormal;
    fragUv = inUv;
    gl_Position = uProjection * uView * worldPos;
}
