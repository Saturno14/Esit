package src.body;

import com.github.stephengold.joltjni.Jolt;
import com.github.stephengold.joltjni.NativeLibraryLoader;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Carica la libreria nativa di Jolt (jolt-jni) e la inizializza una volta per processo.
 *
 * jolt-jni non si carica i nativi da solo: il jar con classifier (vedi le proprieta' jolt.* nel
 * pom) contiene joltjni.dll sotto "/windows/x86-64/com/github/stephengold/". Qui lo si estrae in
 * un file temporaneo e si chiama System.load, come fa LWJGL con i suoi nativi.
 *
 * La simulazione deve poter girare ANCHE senza fisica (SimRunner su un'altra piattaforma, o jar
 * dei nativi mancante): per questo {@link #tryLoad()} ritorna false invece di lanciare, e chi usa
 * la fisica deve chiederlo prima. {@link #error()} dice perche' non si e' caricata.
 */
public final class PhysicsNatives {

    private static boolean attempted = false;
    private static boolean loaded = false;
    private static String error = null;

    private PhysicsNatives() {
    }

    /** Carica e inizializza Jolt al primo uso. Ritorna false se i nativi non ci sono. */
    public static synchronized boolean tryLoad() {
        if (attempted) {
            return loaded;
        }
        attempted = true;
        try {
            String resource = resourcePath();
            Path temp = extract(resource);
            if (!NativeLibraryLoader.loadLibrary(temp.toAbsolutePath().toString())) {
                error = "System.load ha rifiutato " + temp;
                return false;
            }
            // Sequenza di avvio richiesta da Jolt: allocatore, callback, factory, tipi di forma.
            Jolt.registerDefaultAllocator();
            Jolt.installDefaultAssertCallback();
            Jolt.installDefaultTraceCallback();
            if (!Jolt.newFactory()) {
                error = "Jolt.newFactory() ha fallito";
                return false;
            }
            Jolt.registerTypes();
            loaded = true;
            return true;
        } catch (Throwable t) {
            error = t.toString();
            return false;
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /** Motivo del mancato caricamento (null se la fisica e' disponibile o non e' stata richiesta). */
    public static String error() {
        return error;
    }

    /** Come tryLoad ma lancia: da usare solo negli strumenti dedicati alla fisica (benchmark, test). */
    public static void require() {
        if (!tryLoad()) {
            throw new IllegalStateException("Fisica Jolt non disponibile: " + error
                    + "\nControlla le proprieta' jolt.platform / jolt.flavor nel pom e lancia 'mvn -q compile'.");
        }
    }

    /** Versione di Jolt in uso (per i log); stringa vuota se i nativi non sono caricati. */
    public static String version() {
        return loaded ? Jolt.versionString() : "";
    }

    private static String resourcePath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String dir;
        String file;
        if (os.contains("win")) {
            dir = "windows";
            file = "joltjni.dll";
        } else if (os.contains("mac") || os.contains("darwin")) {
            dir = "osx";
            file = "libjoltjni.dylib";
        } else {
            dir = "linux";
            file = "libjoltjni.so";
        }
        String cpu = arch.contains("aarch64") || arch.contains("arm64") ? "aarch64" : "x86-64";
        return "/" + dir + "/" + cpu + "/com/github/stephengold/" + file;
    }

    private static Path extract(String resource) throws Exception {
        try (InputStream in = PhysicsNatives.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("nativo non trovato sul classpath: " + resource
                        + " (manca la dipendenza jolt-jni con classifier?)");
            }
            int dot = resource.lastIndexOf('.');
            Path temp = Files.createTempFile("esit-jolt", resource.substring(dot));
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            temp.toFile().deleteOnExit();
            return temp;
        }
    }
}
