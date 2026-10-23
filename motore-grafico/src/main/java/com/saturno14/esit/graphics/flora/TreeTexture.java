package com.saturno14.esit.graphics.flora;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Texture procedurali dell'albero, in un array di due strati: corteccia e fogliame.
 *
 * PERCHE' UN ARRAY E NON DUE TEXTURE. Tronco e chioma si disegnano uno dietro l'altro per ogni
 * albero: con due texture separate servirebbe un cambio di binding per pezzo, cioe' due cambi per
 * albero. Con un array si lega una volta e si cambia solo un intero (lo strato) nello shader.
 *
 * PERCHE' PROCEDURALI. Come per la mela e per i materiali del terreno: nessun file esterno da
 * distribuire, e il rumore si puo' rendere PERIODICO sul lato della texture, cosa che una foto non
 * e'. Un rumore periodico si ripete senza cucitura, e su un tronco - dove la texture gira attorno
 * al cilindro e si ripete in altezza - una cucitura si vedrebbe come una riga verticale netta.
 *
 * LA CORTECCIA E' FATTA DI SOLCHI VERTICALI, non di macchie: la direzione conta. Un rumore
 * isotropo su un tronco si legge come pietra, non come legno, perche' e' l'allungamento verticale
 * delle fibre a dire all'occhio di che materiale si tratta.
 */
public class TreeTexture {

    public static final int SIZE = 128;
    /** Strato della corteccia (tronco). */
    public static final int LAYER_BARK = 0;
    /** Strato del fogliame (chioma). */
    public static final int LAYER_LEAVES = 1;
    private static final int LAYERS = 2;

    private final int id;

    public TreeTexture() {
        ByteBuffer buffer = BufferUtils.createByteBuffer(SIZE * SIZE * LAYERS * 4);
        for (int layer = 0; layer < LAYERS; layer++) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    float[] c = layer == LAYER_BARK ? bark(x, y) : leaves(x, y);
                    buffer.put((byte) clamp255(c[0]));
                    buffer.put((byte) clamp255(c[1]));
                    buffer.put((byte) clamp255(c[2]));
                    buffer.put((byte) 255);
                }
            }
        }
        buffer.flip();

        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, SIZE, SIZE, LAYERS, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D_ARRAY, id);
    }

    public void destroy() {
        glDeleteTextures(id);
    }

    // ------------------------------------------------------------------ corteccia

    /**
     * Corteccia: solchi verticali.
     *
     * Il rumore e' campionato con la coordinata verticale COMPRESSA (fibre lunghe) e quella
     * orizzontale dilatata (solchi stretti): e' questa anisotropia a fare il legno. Poi un gradino
     * morbido trasforma il rumore in creste e valli invece di una sfumatura continua, perche' la
     * corteccia ha spacchi con un bordo, non macchie.
     */
    private static float[] bark(int x, int y) {
        float u = (x + 0.5f) / SIZE;
        float v = (y + 0.5f) / SIZE;

        // fibre: molte ripetizioni in orizzontale, poche in verticale
        float fibre = fbm(u * 7f, v * 1.4f, 7, 1);
        // spacchi profondi, piu' rari
        float crack = fbm(u * 3f, v * 0.8f, 3, 37);

        float groove = smoothstep(0.42f, 0.62f, fibre);
        float[] dark = {62f, 44f, 30f};
        float[] light = {126f, 96f, 66f};
        float[] c = mix(dark, light, groove);

        // gli spacchi scuriscono molto: sono fessure, non ombre
        float deep = smoothstep(0.70f, 0.86f, crack);
        c = mix(c, new float[]{38f, 26f, 18f}, deep * 0.85f);

        // grana fine, per non avere superfici piatte da vicino
        float grain = hash(x * 3 + 11, y * 7 + 5) * 16f - 8f;
        c[0] += grain;
        c[1] += grain * 0.8f;
        c[2] += grain * 0.6f;
        return c;
    }

    // ------------------------------------------------------------------ fogliame

    /**
     * Fogliame: grumi di foglie.
     *
     * Qui il rumore e' isotropo, al contrario della corteccia: una chioma vista da fuori e' un
     * ammasso senza direzione. Due scale sovrapposte - grumi larghi che fanno il volume, foglie
     * piccole che danno il dettaglio - piu' qualche punto scuro che fa da buco tra i rami: senza
     * i buchi una chioma si legge come una palla di plastica verde.
     */
    private static float[] leaves(int x, int y) {
        float u = (x + 0.5f) / SIZE;
        float v = (y + 0.5f) / SIZE;

        float clump = fbm(u * 3f, v * 3f, 3, 7);
        float detail = fbm(u * 9f, v * 9f, 9, 71);

        float[] darkGreen = {28f, 62f, 26f};
        float[] midGreen = {62f, 116f, 44f};
        float[] lightGreen = {104f, 158f, 66f};

        float[] c = mix(darkGreen, midGreen, smoothstep(0.35f, 0.65f, clump));
        c = mix(c, lightGreen, smoothstep(0.58f, 0.82f, detail) * 0.7f);

        // buchi tra i rami: si vedrebbe il cielo, qui li facciamo come ombra profonda
        float hole = fbm(u * 5f, v * 5f, 5, 211);
        c = mix(c, new float[]{16f, 30f, 14f}, smoothstep(0.78f, 0.93f, hole));

        float grain = hash(x * 5 + 3, y * 3 + 17) * 18f - 9f;
        c[0] += grain * 0.6f;
        c[1] += grain;
        c[2] += grain * 0.5f;
        return c;
    }

    // ------------------------------------------------------------------ rumore periodico

    /**
     * Rumore frattale a tre ottave, PERIODICO su entrambi gli assi con il periodo dato: cosi' la
     * texture si ripete senza cuciture, che su un tronco sarebbero una riga verticale netta.
     */
    private static float fbm(float fu, float fv, int period, int seed) {
        float sum = 0f;
        float amp = 0.5f;
        float total = 0f;
        int p = period;
        float su = fu;
        float sv = fv;
        for (int octave = 0; octave < 3; octave++) {
            sum += amp * valueNoise(su, sv, p, seed + octave * 101);
            total += amp;
            amp *= 0.55f;
            su *= 2f;
            sv *= 2f;
            p *= 2;
        }
        return sum / total;
    }

    private static float valueNoise(float fu, float fv, int period, int seed) {
        int ix0 = (int) Math.floor(fu);
        int iy0 = (int) Math.floor(fv);
        float fx = fu - ix0;
        float fy = fv - iy0;
        int x0 = mod(ix0, period);
        int x1 = mod(ix0 + 1, period);
        int y0 = mod(iy0, period);
        int y1 = mod(iy0 + 1, period);

        float a = hash(x0 + seed, y0);
        float b = hash(x1 + seed, y0);
        float c = hash(x0 + seed, y1);
        float d = hash(x1 + seed, y1);

        float sx = fx * fx * (3f - 2f * fx);
        float sy = fy * fy * (3f - 2f * fy);
        return lerp(lerp(a, b, sx), lerp(c, d, sx), sy);
    }

    private static int mod(int a, int m) {
        return ((a % m) + m) % m;
    }

    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= (h >>> 16);
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)};
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }

    private static int clamp255(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }
}
