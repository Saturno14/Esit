#version 330 core

in vec3 fragNormal;
in vec3 fragWorldPos;
in vec2 fragUv;

uniform vec3 uLightDirection;
uniform vec3 uCameraPos;
uniform sampler2D uTexture;

out vec4 outColor;

void main() {
    vec3 normal = normalize(fragNormal);
    vec3 albedo = texture(uTexture, fragUv).rgb;

    float diffuse = max(dot(normal, -uLightDirection), 0.0);
    float ambient = 0.30;
    float lighting = ambient + diffuse * (1.0 - ambient);

    // Riflesso morbido: la buccia della mela e' lucida
    vec3 viewDir = normalize(uCameraPos - fragWorldPos);
    vec3 halfway = normalize(viewDir - uLightDirection);
    float specular = pow(max(dot(normal, halfway), 0.0), 48.0) * 0.35;

    outColor = vec4(albedo * lighting + vec3(specular), 1.0);
}
