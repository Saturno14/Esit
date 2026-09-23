#version 330 core

in vec3 fragNormal;
in vec3 fragWorldPos;

uniform vec3 uLightDirection;
uniform vec3 uBaseColor;

out vec4 outColor;

void main() {
    vec3 normal = normalize(fragNormal);
    float diffuse = max(dot(normal, -uLightDirection), 0.0);
    float ambient = 0.25;
    float lighting = ambient + diffuse * (1.0 - ambient);
    outColor = vec4(uBaseColor * lighting, 1.0);
}
