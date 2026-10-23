#version 330 core

in vec3 fragNormal;
in vec3 fragWorldPos;
in vec2 fragUv;

uniform vec3 uLightDirection;
uniform sampler2DArray uTexture;
/** Strato: 0 corteccia, 1 fogliame. Vedi TreeTexture. */
uniform int uLayer;
/**
 * Tinta dell'albero: moltiplica la texture. Serve a dare a ogni albero un verde un po' suo e a
 * schiarire quelli giovani - un bosco di cloni dello stesso verde si legge come una superficie
 * sola invece che come tanti alberi.
 */
uniform vec3 uTint;
/** Forza della luce: 1 di giorno, verso 0.1 di notte. Vedi src.DayNight. */
uniform float uLightStrength;
uniform vec3 uSkyColor;

out vec4 outColor;

void main() {
    vec3 normal = normalize(fragNormal);
    vec3 albedo = texture(uTexture, vec3(fragUv, float(uLayer))).rgb * uTint;

    float sun = max(dot(normal, -normalize(uLightDirection)), 0.0);

    /*
     * LA CHIOMA PRENDE LUCE ANCHE DA DIETRO. Il fogliame e' sottile e traslucido: controluce, una
     * chioma vera si accende invece di diventare una sagoma nera. Senza questo termine la meta'
     * in ombra di ogni albero e' un buco piatto, ed e' la cosa che piu' fa sembrare finto un
     * albero fatto di una sfera sola.
     */
    float through = uLayer == 1 ? pow(max(-dot(normal, -normalize(uLightDirection)), 0.0), 2.0) * 0.35 : 0.0;

    float sky = 0.5 + 0.5 * normal.y;
    vec3 lighting = vec3(0.95) * (sun + through) * uLightStrength
                  + uSkyColor * sky * 0.40
                  + vec3(0.05);

    outColor = vec4(albedo * lighting, 1.0);
}
