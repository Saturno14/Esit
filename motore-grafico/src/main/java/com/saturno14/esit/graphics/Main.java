package com.saturno14.esit.graphics;

import com.saturno14.esit.graphics.render.Renderer;
import com.saturno14.esit.graphics.world.ConsoleLauncher;

/**
 * Entry point del motore grafico.
 * Collega la simulazione reale (../simulazione/src/world.java) tramite SimulationWorldBridge, ne
 * renderizza il volume con Surface Nets e all'avvio fa partire anche la simulazione.
 *
 * Controlli: WASD per muoversi, SPACE/SHIFT su/giu', trascinare con il tasto sinistro del
 * mouse per orientare la visuale, ESC per uscire.
 */
public class Main {
    public static void main(String[] args) {
        new Renderer().run();
        // Chiusa la finestra del motore, si chiude anche la finestra della console dei comandi.
        ConsoleLauncher.stop();
        // I thread delle entita' e del ciclo del mondo non sono daemon: chiusa la finestra
        // si termina esplicitamente il processo, altrimenti la simulazione resterebbe in vita.
        System.exit(0);
    }
}
