#version 330 core

in vec3 fragWorldPos;
in vec3 fragNormal;
in vec4 fragMaterials;
in float fragAo;

uniform vec3 uLightDirection;
uniform sampler2DArray uMaterials;
uniform float uTexScale;        // ripetizioni di texture per metro di mondo
uniform vec3 uSkyColor;
/** Forza del sole: 1 di giorno, verso 0.1 di notte (la luna). Vedi src.DayNight. */
uniform float uSunStrength;

out vec4 outColor;

/**
 * PROIEZIONE TRIPLANARE. Una superficie estratta da un campo di densita' non ha coordinate di
 * texture: non c'e' una griglia di quadrati da cui ricavare un (u,v), e qualunque proiezione
 * singola si stira a zero sulle facce parallele al suo asse. Si campiona quindi la stessa texture
 * tre volte - proiettata sui tre piani del mondo - e si mescolano le tre in base a quanto la
 * normale punta lungo ciascun asse. L'esponente alto sui pesi rende la transizione piu' stretta:
 * con pesi lineari la texture appare "doppia" e sfocata su tutte le superfici inclinate.
 */
vec3 triplanar(float layer, vec3 p, vec3 w) {
    vec3 cx = texture(uMaterials, vec3(p.yz * uTexScale, layer)).rgb;
    vec3 cy = texture(uMaterials, vec3(p.xz * uTexScale, layer)).rgb;
    vec3 cz = texture(uMaterials, vec3(p.xy * uTexScale, layer)).rgb;
    return cx * w.x + cy * w.y + cz * w.z;
}

void main() {
    vec3 normal = normalize(fragNormal);

    vec3 w = abs(normal);
    w = pow(w, vec3(4.0));
    w /= max(w.x + w.y + w.z, 1e-4);

    // i quattro materiali, mescolati con i pesi del vertice: i confini sono sfumati
    vec3 albedo = vec3(0.0);
    float total = 0.0;
    for (int i = 0; i < 4; i++) {
        float weight = fragMaterials[i];
        if (weight > 0.004) {
            albedo += weight * triplanar(float(i), fragWorldPos, w);
            total += weight;
        }
    }
    albedo = total > 0.0 ? albedo / total : vec3(0.5);

    // luce: sole diffuso, piu' una luce di cielo che viene dall'alto. L'occlusione ambientale
    // spegne la luce di cielo (non il sole): e' quello che fa leggere gli avvallamenti
    float sun = max(dot(normal, -normalize(uLightDirection)), 0.0);
    float sky = 0.5 + 0.5 * normal.y;
    vec3 lighting = vec3(0.92) * sun * fragAo * uSunStrength
                  + uSkyColor * sky * 0.45 * fragAo
                  + vec3(0.04);

    outColor = vec4(albedo * lighting, 1.0);
}
