package com.saturno14.esit.graphics;

import com.saturno14.esit.graphics.render.Renderer;

/**
 * Entry point del motore grafico.
 * Collega la simulazione reale (../simulazione/src/world.java) tramite SimulationWorldBridge e ne
 * renderizza il volume con Surface Nets.
 *
 * Controlli: WASD per muoversi, mouse per guardarsi intorno, SPACE/SHIFT su/giu', ESC per uscire.
 */
public class Main {
    public static void main(String[] args) {
        new Renderer().run();
    }
}
