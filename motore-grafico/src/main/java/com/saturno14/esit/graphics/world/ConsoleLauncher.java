package com.saturno14.esit.graphics.world;

import org.json.JSONObject;
import src.utils;

import java.io.File;
import java.nio.file.Path;

/**
 * Apre la console della simulazione (ConsoleInput, in una finestra cmd separata) e collega
 * a essa il client della simulazione (utils.client()), esattamente come fa main.java quando
 * si avvia la simulazione da sola.
 *
 * ConsoleInput e' compilata da Maven insieme al resto (../simulazione e' source root), quindi
 * si lancia dal classpath gia' compilato invece che in modalita' source-file.
 */
public final class ConsoleLauncher {

    private ConsoleLauncher() {
    }

    public static void start() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            System.err.println("[ConsoleLauncher] Console esterna supportata solo su Windows (usa cmd): salto.");
            return;
        }
        try {
            String classesDir = locationOf(utils.class);
            String jsonJar = locationOf(JSONObject.class);
            String classpath = classesDir + File.pathSeparator + jsonJar;

            new ProcessBuilder("cmd", "/c", "start", "Esit Console", "cmd", "/c",
                    "java -cp " + classpath + " ConsoleInput").start();

            // utils.client() ritenta la connessione finche' la console non e' pronta: va in un
            // thread a parte per non bloccare l'avvio del motore grafico.
            Thread client = new Thread(utils::client, "esit-console-client");
            client.setDaemon(true);
            client.start();
        } catch (Exception e) {
            System.err.println("[ConsoleLauncher] Impossibile avviare la console: " + e.getMessage());
        }
    }

    /**
     * Chiude la finestra della console (cercata per titolo "Esit Console"): da chiamare quando
     * si chiude il motore grafico, cosi' non resta aperta una console orfana. La console e' un
     * processo cmd separato avviato con "start", quindi non abbiamo un suo handle diretto: la
     * si chiude cercandola per titolo con taskkill.
     */
    public static void stop() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }
        try {
            new ProcessBuilder("cmd", "/c", "taskkill", "/FI", "WINDOWTITLE eq Esit Console", "/T", "/F")
                    .start()
                    .waitFor();
        } catch (Exception e) {
            System.err.println("[ConsoleLauncher] Impossibile chiudere la console: " + e.getMessage());
        }
    }

    private static String locationOf(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    }
}
