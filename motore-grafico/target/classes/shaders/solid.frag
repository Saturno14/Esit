#version 330 core

in vec3 fragNormal;
in vec3 fragWorldPos;

uniform vec3 uLightDirection;
uniform vec3 uBaseColor;
/** Forza della luce: 1 di giorno, verso 0.15 di notte. Vedi src.DayNight. */
uniform float uLightStrength;

out vec4 outColor;

void main() {
    vec3 normal = normalize(fragNormal);
    float diffuse = max(dot(normal, -uLightDirection), 0.0);
    float ambient = 0.25;
    float lighting = (ambient + diffuse * (1.0 - ambient)) * uLightStrength;
    outColor = vec4(uBaseColor * lighting, 1.0);
}
