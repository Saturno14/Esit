package com.saturno14.esit.graphics.render;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Texture procedurale dell'acqua, 128x128 e PERIODICA su entrambi i lati.
 *
 * COSA CONTIENE E PERCHE' NON UN COLORE. Nei canali RGB non c'e' un colore d'acqua: c'e' la FORMA
 * delle onde (una mappa di normali compressa in due canali) piu' una mappa di schiuma. Il colore
 * lo fa lo shader, perche' il colore dell'acqua non e' una proprieta' della superficie - dipende
 * da come la si guarda: di scorcio riflette il cielo, dall'alto si vede il fondo. Mettere un
 * azzurro nella texture darebbe una vernice blu piatta, che e' esattamente l'aspetto che
 * l'acqua finta ha.
 *
 *   R, G : pendenza dell'onda lungo x e z, da 0 a 255 con 128 = piatto
 *   B    : schiuma, concentrata sulle creste
 *
 * LA PERIODICITA' E' OBBLIGATORIA: lo shader campiona questa texture due volte, con due scale e
 * due velocita' diverse, e la somma (vedi water.frag). Serve a far sparire il periodo: una sola
 * copia che scorre si riconosce subito come un disegno che si ripete, due copie sfasate no. Ma
 * entrambe tornano a ripetersi, quindi se la texture non fosse periodica si vedrebbero DUE
 * griglie di cuciture invece di una.
 */
public class WaterTexture {

    public static final int SIZE = 128;

    private final int id;

    public WaterTexture() {
        // prima l'altezza dell'onda, poi da quella si ricava la pendenza per differenze finite:
        // calcolare la pendenza direttamente dal rumore darebbe normali che non corrispondono a
        // nessuna superficie, e la luce ci si legge come sporco invece che come onde
        float[] height = new float[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                height[y * SIZE + x] = waveHeight(x, y);
            }
        }

        ByteBuffer buffer = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float hl = height[y * SIZE + wrap(x - 1)];
                float hr = height[y * SIZE + wrap(x + 1)];
                float hd = height[wrap(y - 1) * SIZE + x];
                float hu = height[wrap(y + 1) * SIZE + x];
                float dx = (hr - hl) * 0.5f;
                float dz = (hu - hd) * 0.5f;

                float h = height[y * SIZE + x];
                // schiuma solo sulle creste piu' alte, e con un bordo: la schiuma ha un contorno
                float foam = smoothstep(0.78f, 0.95f, h);

                buffer.put((byte) clamp255(128f + dx * 900f));
                buffer.put((byte) clamp255(128f + dz * 900f));
                buffer.put((byte) clamp255(foam * 255f));
                buffer.put((byte) 255);
            }
        }
        buffer.flip();

        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SIZE, SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        glGenerateMipmap(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, id);
    }

    public void destroy() {
        glDeleteTextures(id);
    }

    // ------------------------------------------------------------------ generazione

    /**
     * Altezza dell'onda in un punto: rumore frattale piu' due treni d'onda incrociati.
     *
     * Il rumore da solo fa una superficie grumosa, che somiglia a un terreno visto da vicino, non
     * ad acqua. Quello che rende l'acqua riconoscibile e' che le onde hanno una DIREZIONE: due
     * sinusoidi obliquee, deformate dal rumore, danno le creste allungate e parallele che l'occhio
     * legge come acqua anche in una pozza ferma.
     */
    private static float waveHeight(int x, int y) {
        float u = (x + 0.5f) / SIZE;
        float v = (y + 0.5f) / SIZE;

        float n = fbm(u * 4f, v * 4f, 4, 17);

        // due treni d'onda, periodi interi cosi' restano periodici sul lato della texture
        float w1 = (float) Math.sin((u * 3 + v * 1) * 2 * Math.PI + n * 3.4);
        float w2 = (float) Math.sin((u * 1 - v * 4) * 2 * Math.PI + n * 2.1);

        float h = 0.5f + 0.26f * w1 + 0.16f * w2 + 0.22f * (n - 0.5f);
        return Math.max(0f, Math.min(1f, h));
    }

    private static int wrap(int i) {
        return ((i % SIZE) + SIZE) % SIZE;
    }

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
