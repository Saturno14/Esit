#version 330 core

in vec3 fragNormal;
in vec3 fragWorldPos;
in vec2 fragUv;

uniform vec3 uLightDirection;
uniform vec3 uCameraPos;
uniform sampler2D uTexture;
/**
 * Maturazione da 0 (appena allegata) a 1 (marcia): vedi src.Flora.Apple.ripeness.
 * Il colore la segue - verde, rossa, bruna - cosi' si capisce a che punto e' una mela guardandola
 * invece di leggere un log, ed e' la stessa informazione su cui l'entita' decide se mangiarla.
 */
uniform float uRipeness;
/** Forza della luce: 1 di giorno, verso 0.1 di notte. Vedi src.DayNight. */
uniform float uLightStrength;

out vec4 outColor;

void main() {
    vec3 normal = normalize(fragNormal);
    vec3 albedo = texture(uTexture, fragUv).rgb;

    // verde da immatura, poi il rosso della texture, poi il bruno del marcio
    vec3 unripe = vec3(0.42, 0.56, 0.20);
    vec3 rotten = vec3(0.30, 0.22, 0.13);
    float green = clamp(1.0 - uRipeness / 0.37, 0.0, 1.0);
    float brown = clamp((uRipeness - 0.78) / 0.22, 0.0, 1.0);
    albedo = mix(albedo, unripe, green * 0.85);
    albedo = mix(albedo, rotten, brown * 0.8);

    float diffuse = max(dot(normal, -uLightDirection), 0.0);
    float ambient = 0.30;
    float lighting = (ambient + diffuse * (1.0 - ambient)) * uLightStrength;

    // Riflesso morbido: la buccia della mela e' lucida
    vec3 viewDir = normalize(uCameraPos - fragWorldPos);
    vec3 halfway = normalize(viewDir - uLightDirection);
    float specular = pow(max(dot(normal, halfway), 0.0), 48.0) * 0.35
                   * uLightStrength * (1.0 - brown);     // il marcio non e' lucido

    outColor = vec4(albedo * lighting + vec3(specular), 1.0);
}
