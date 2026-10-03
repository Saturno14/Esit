package com.saturno14.esit.graphics.apple;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33.*;

/**
 * Texture procedurale 128x128 della mela, generata in codice (nessun file esterno).
 *
 * Layout (v va dall'alto al basso):
 *   - striscia alta (v < STRIP_V): a sinistra marrone del gambo, a destra verde della foglia;
 *     e' campionata solo dalla geometria di gambo e foglia (vedi AppleMesh);
 *   - resto: buccia della mela (rosso con sfumature giallo-verdi, striature verticali,
 *     lenticelle chiare, cavita' scura attorno al gambo e in fondo).
 * L'asse u e' ripetibile (le noise sono periodiche in u) cosi' non c'e' cucitura.
 */
public class AppleTexture {

    public static final int SIZE = 128;
    /** Frazione in altezza riservata a gambo e foglia. */
    public static final float STRIP_V = 0.08f;

    private final int id;

    public AppleTexture() {
        ByteBuffer buffer = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float[] c = pixel(x, y);
                buffer.put((byte) clamp255(c[0]));
                buffer.put((byte) clamp255(c[1]));
                buffer.put((byte) clamp255(c[2]));
                buffer.put((byte) 255);
            }
        }
        buffer.flip();

        id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
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

    private static float[] pixel(int x, int y) {
        float u = (x + 0.5f) / SIZE;
        float v = (y + 0.5f) / SIZE;

        if (v < STRIP_V) {
            return u < 0.5f ? stemPixel(x, y) : leafPixel(u, x, y);
        }

        float t = (v - STRIP_V) / (1f - STRIP_V); // 0 = polo superiore, 1 = polo inferiore

        float n = fbm(u, t);
        float[] c = {196f, 28f, 36f};

        // Sfumature gialloverdi (la mela non e' mai rossa uniforme)
        float blush = smoothstep(0.50f, 0.66f, n) * 0.75f;
        c = mix(c, new float[]{208f, 178f, 62f}, blush);

        // Striature verticali leggere
        float streak = 0.5f + 0.5f * (float) Math.sin(u * 2.0 * Math.PI * 14.0 + n * 7.0);
        float shade = 0.86f + 0.14f * streak;
        c[0] *= shade;
        c[1] *= shade;
        c[2] *= shade;

        // Lenticelle: puntini chiari sparsi
        if (t > 0.15f && t < 0.90f) {
            int cellX = x / 4;
            int cellY = y / 4;
            if (hash(cellX * 7 + 13, cellY * 5 + 3) < 0.30f) {
                float dx = (x % 4) + 0.5f - 2f;
                float dy = (y % 4) + 0.5f - 2f;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < 1.3f) {
                    c = mix(c, new float[]{236f, 214f, 150f}, 0.45f * (1f - d / 1.3f));
                }
            }
        }

        // Cavita' scura attorno al gambo (in alto) e calice (in basso)
        if (t < 0.14f) {
            c = mix(c, new float[]{105f, 68f, 38f}, (float) Math.pow(1f - t / 0.14f, 1.6));
        }
        if (t > 0.93f) {
            c = mix(c, new float[]{88f, 58f, 34f}, (t - 0.93f) / 0.07f);
        }
        return c;
    }

    private static float[] stemPixel(int x, int y) {
        float grain = hash(x * 3, y * 5) * 22f - 11f;
        return new float[]{98f + grain, 64f + grain * 0.7f, 36f + grain * 0.5f};
    }

    private static float[] leafPixel(float u, int x, int y) {
        float grain = hash(x * 5, y * 3) * 20f - 10f;
        float[] c = {58f + grain * 0.5f, 128f + grain, 44f + grain * 0.4f};
        if (Math.abs(u - 0.75f) < 0.012f) { // nervatura centrale
            c = mix(c, new float[]{110f, 175f, 80f}, 0.8f);
        }
        return c;
    }

    // ------------------------------------------------------------------ noise / utilita'

    /** Rumore frattale (3 ottave), periodico lungo u, normalizzato in ~[0,1]. */
    private static float fbm(float u, float t) {
        float sum = 0f;
        float amp = 0.5f;
        float total = 0f;
        int period = 4;
        for (int octave = 0; octave < 3; octave++) {
            sum += amp * valueNoise(u * period, t * period, period, octave * 101);
            total += amp;
            amp *= 0.6f;
            period *= 2;
        }
        return sum / total;
    }

    private static float valueNoise(float fu, float fv, int period, int seed) {
        int ix0 = (int) Math.floor(fu);
        int iy0 = (int) Math.floor(fv);
        float fx = fu - ix0;
        float fy = fv - iy0;
        int x0 = ((ix0 % period) + period) % period;
        int x1 = (((ix0 + 1) % period) + period) % period;

        float a = hash(x0 + seed, iy0);
        float b = hash(x1 + seed, iy0);
        float c = hash(x0 + seed, iy0 + 1);
        float d = hash(x1 + seed, iy0 + 1);

        float sx = fx * fx * (3f - 2f * fx);
        float sy = fy * fy * (3f - 2f * fy);
        return lerp(lerp(a, b, sx), lerp(c, d, sx), sy);
    }

    /** Hash deterministico intero -> [0,1). */
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
