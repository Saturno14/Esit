package com.saturno14.esit.graphics.apple;

import src.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Legge dalla simulazione (solo lettura) le posizioni delle mele presenti nel mondo.
 * Nella simulazione una mela e' una cella il cui oggetto e' "M" (world.getSymbol).
 */
public final class AppleField {

    private AppleField() {
    }

    /** Ritorna le celle {x, y, z} che contengono una mela in questo momento. */
    public static List<int[]> scan() {
        List<int[]> apples = new ArrayList<>();
        int dim = world.getDim();
        for (int x = 0; x < dim; x++) {
            for (int y = 0; y < dim; y++) {
                for (int z = 0; z < dim; z++) {
                    try {
                        if ("M".equals(world.getSymbol(x, y, z))) {
                            apples.add(new int[]{x, y, z});
                        }
                    } catch (Exception e) {
                        // cella non ancora inizializzata (es. mondo in ricreazione): la salto
                    }
                }
            }
        }
        return apples;
    }
}
