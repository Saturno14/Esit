package com.saturno14.esit.graphics.world;

import org.joml.Vector3f;

/**
 * Unica fonte di verita' per il mapping tra coordinate di griglia (intere, usate dalla
 * simulazione) e coordinate mondo (continue, usate dal rendering).
 */
public final class GridToWorld {

    /** Dimensione di una cella della griglia in unita' mondo. */
    public static final float CELL_SIZE = 1.0f;

    private GridToWorld() {
    }

    public static Vector3f toWorld(int gridX, int gridY, int gridZ) {
        return new Vector3f(
                gridX * CELL_SIZE,
                gridY * CELL_SIZE,
                gridZ * CELL_SIZE
        );
    }

    public static Vector3f toWorld(float gridX, float gridY, float gridZ) {
        return new Vector3f(
                gridX * CELL_SIZE,
                gridY * CELL_SIZE,
                gridZ * CELL_SIZE
        );
    }
}
