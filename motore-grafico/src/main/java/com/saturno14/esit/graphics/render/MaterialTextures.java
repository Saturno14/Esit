package com.saturno14.esit.graphics.render;

import com.saturno14.esit.graphics.world.VoxelMaterial;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL33.*;

/**
 * Le texture dei materiali del terreno, in un unico array di texture (uno strato per materiale),
 * GENERATE a runtime invece di caricate da file.
 *
 * Perche' generate: il progetto non ha una cartella di immagini e non voglio introdurre file
 * binari da versionare per avere della roccia che sembri roccia. Lo stesso approccio e' gia' usato
 * per le mele (apple/AppleTexture). Il rumore e' PERIODICO sul lato della texture, quindi la
 * texture si ripete senza giunture visibili - cosa necessaria qui, perche' la proiezione
 * triplanare la ripete su tutto il terreno.
 *
 * Un array di texture e non quattro texture separate perche' lo shader deve poter mescolare
 * quattro materiali in un solo passaggio, e con quattro campionatori separati si sprecano unita'
 * di texture e si complica il codice per niente.
 */
public class MaterialTextures {

    private static final int SIZE = 128;

    private final int textureId;

    public MaterialTextures() {
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, SIZE, SIZE, VoxelMaterial.COUNT,
                0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        upload(VoxelMaterial.ROCK, rock());
        upload(VoxelMaterial.DIRT, dirt());
        upload(VoxelMaterial.GRASS, grass());
        upload(VoxelMaterial.SAND, sand());

        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);
    }

    public void destroy() {
        glDeleteTextures(textureId);
    }

    private void upload(int layer, ByteBuffer pixels) {
        glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer, SIZE, SIZE, 1,
                GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    }

    // ------------------------------------------------------------------ i quattro materiali

    /** Roccia: macchie grosse e crepe sottili. */
    private static ByteBuffer rock() {
        Noise n = new Noise(11);
        return build(VoxelMaterial.ROCK, (x, y) -> {
            double base = 0.75 + 0.45 * n.fbm(x, y, 4, 6);
            double crack = n.fbm(x, y, 2, 19);
            if (crack > 0.62) {
                base *= 0.55;       // crepa
            }
            return base;
        });
    }

    /** Terra: grana fine, qualche sasso piu' chiaro. */
    private static ByteBuffer dirt() {
        Noise n = new Noise(23);
        return build(VoxelMaterial.DIRT, (x, y) -> {
            double base = 0.80 + 0.40 * n.fbm(x, y, 4, 12);
            double stone = n.fbm(x, y, 1, 32);
            return stone > 0.70 ? base * 1.35 : base;
        });
    }

    /** Erba: ciuffi, variazione piu' forte in verticale che in orizzontale. */
    private static ByteBuffer grass() {
        Noise n = new Noise(37);
        return build(VoxelMaterial.GRASS, (x, y) -> {
            double tufts = n.fbm(x * 0.5, y * 2.0, 3, 16);
            return 0.72 + 0.56 * tufts;
        });
    }

    /** Sabbia: granulosa e quasi uniforme, con onde larghe. */
    private static ByteBuffer sand() {
        Noise n = new Noise(53);
        return build(VoxelMaterial.SAND, (x, y) -> {
            double grain = n.fbm(x, y, 2, 48);
            double ripple = Math.sin((x * 6.0 + n.fbm(x, y, 2, 4) * 3.0) * Math.PI * 2) * 0.5 + 0.5;
            return 0.88 + 0.12 * grain + 0.10 * ripple;
        });
    }

    private interface Shade {
        /** Luminosita' (circa 0.4..1.6) nel punto (x,y), con x e y in 0..1. */
        double at(double x, double y);
    }

    private static ByteBuffer build(int material, Shade shade) {
        float[] color = VoxelMaterial.COLOR[material];
        ByteBuffer buf = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                double v = shade.at(px / (double) SIZE, py / (double) SIZE);
                buf.put(channel(color[0] * v));
                buf.put(channel(color[1] * v));
                buf.put(channel(color[2] * v));
                buf.put((byte) 255);
            }
        }
        buf.flip();
        return buf;
    }

    private static byte channel(double v) {
        int i = (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
        return (byte) i;
    }

    /**
     * Rumore a valori con reticolo PERIODICO: il punto (1,y) vale come il punto (0,y), quindi la
     * texture si ripete senza giunture. Senza la periodicita' la proiezione triplanare mostrerebbe
     * una griglia di cuciture su tutto il terreno.
     */
    private static final class Noise {

        private final double[] lattice;
        private final int period = 16;

        Noise(long seed) {
            Random rng = new Random(seed);
            lattice = new double[period * period];
            for (int i = 0; i < lattice.length; i++) {
                lattice[i] = rng.nextDouble();
            }
        }

        /** Somma di piu' ottave, riportata in 0..1. */
        double fbm(double x, double y, int octaves, double frequency) {
            double sum = 0, amp = 1, total = 0;
            double f = frequency;
            for (int o = 0; o < octaves; o++) {
                sum += amp * value(x * f, y * f);
                total += amp;
                amp *= 0.5;
                f *= 2;
            }
            return sum / total;
        }

        private double value(double x, double y) {
            int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
            double fx = smooth(x - x0), fy = smooth(y - y0);
            double v00 = at(x0, y0), v10 = at(x0 + 1, y0);
            double v01 = at(x0, y0 + 1), v11 = at(x0 + 1, y0 + 1);
            return lerp(lerp(v00, v10, fx), lerp(v01, v11, fx), fy);
        }

        private double at(int x, int y) {
            int ix = Math.floorMod(x, period);
            int iy = Math.floorMod(y, period);
            return lattice[iy * period + ix];
        }

        private static double smooth(double t) {
            return t * t * (3 - 2 * t);
        }

        private static double lerp(double a, double b, double t) {
            return a + (b - a) * t;
        }
    }
}
