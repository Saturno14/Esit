#version 330 core

in vec3 fragWorldPos;
in vec2 fragUv;

uniform vec3 uLightDirection;
uniform vec3 uCameraPos;
uniform sampler2D uTexture;
uniform vec3 uSkyColor;
/** Forza della luce: 1 di giorno, verso 0.1 di notte. Vedi src.DayNight. */
uniform float uLightStrength;
/** Secondi dall'avvio: fa scorrere le onde. */
uniform float uTime;

out vec4 outColor;

/** Colore dell'acqua guardata dall'alto (si vede il fondo) e di scorcio (riflette). */
const vec3 SHALLOW = vec3(0.10, 0.34, 0.33);
const vec3 DEEP = vec3(0.03, 0.12, 0.20);

void main() {
    /*
     * DUE COPIE DELLA STESSA TEXTURE, con scale e velocita' diverse e direzioni opposte. Una sola
     * copia che scorre si riconosce immediatamente come un disegno che si ripete sempre uguale;
     * due copie con periodi incommensurabili danno un'interferenza che non si ripete nell'arco in
     * cui uno guarda. E' il trucco piu' vecchio dell'acqua in tempo reale, e funziona perche'
     * l'occhio riconosce la periodicita', non il dettaglio.
     */
    vec2 uv1 = fragUv * 0.25 + vec2(uTime * 0.012, uTime * 0.020);
    vec2 uv2 = fragUv * 0.11 - vec2(uTime * 0.017, uTime * 0.009);

    vec3 t1 = texture(uTexture, uv1).rgb;
    vec3 t2 = texture(uTexture, uv2).rgb;

    // le pendenze si SOMMANO (sono derivate, quindi additive), la schiuma si prende la maggiore
    vec2 slope = (t1.rg - 0.5) + (t2.rg - 0.5);
    float foam = max(t1.b, t2.b) * 0.6;

    vec3 normal = normalize(vec3(-slope.x * 1.6, 1.0, -slope.y * 1.6));

    vec3 viewDir = normalize(uCameraPos - fragWorldPos);
    vec3 lightDir = normalize(uLightDirection);

    /*
     * FRESNEL. Quanto l'acqua riflette dipende dall'angolo: guardata a picco e' quasi trasparente
     * e si vede il fondo, guardata di taglio e' uno specchio. E' il motivo per cui una superficie
     * d'acqua ha un colore diverso vicino a chi guarda e lontano, e senza questo termine l'acqua
     * resta una lastra dello stesso colore - l'errore che la fa sembrare vetro colorato.
     */
    float facing = max(dot(normal, viewDir), 0.0);
    float fresnel = 0.03 + 0.97 * pow(1.0 - facing, 4.0);

    vec3 body = mix(SHALLOW, DEEP, pow(1.0 - facing, 1.4));
    vec3 reflected = uSkyColor * 1.05;

    // riflesso del sole: strettissimo, e' quello che fa luccicare il pelo dell'acqua
    vec3 halfway = normalize(viewDir - lightDir);
    float specular = pow(max(dot(normal, halfway), 0.0), 220.0) * 1.8 * uLightStrength;

    float diffuse = max(dot(normal, -lightDir), 0.0);
    vec3 color = body * (0.30 + 0.70 * diffuse) * uLightStrength
               + uSkyColor * 0.18;
    color = mix(color, reflected, fresnel * 0.75);
    color += vec3(specular);
    color = mix(color, vec3(0.80, 0.86, 0.88) * uLightStrength, foam * 0.5);

    // piu' opaca di taglio che a picco, per la stessa ragione del Fresnel
    float alpha = 0.62 + 0.33 * fresnel;
    outColor = vec4(color, alpha);
}
