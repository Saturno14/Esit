package com.saturno14.esit.graphics.world;

import com.saturno14.esit.graphics.mesh.MeshData;
import com.saturno14.esit.graphics.mesh.VoxelMesher;
import src.body.MondoFisico;
import src.body.PhysicsWorld;
import src.world;

import java.util.Locale;

/**
 * LA PROVA DEL SUOLO: il terreno che si DISEGNA e il terreno su cui i corpi APPOGGIANO sono la
 * stessa superficie.
 *
 * Non si deduce dal codice, si MISURA: si costruisce il mondo della simulazione, si genera la
 * mesh del terreno con lo stesso mesher del motore grafico (senza finestra: la mesh e' un array
 * di vertici), e per ogni vertice si confronta la sua quota con quella del campo di altezze che
 * la fisica consegna a Jolt nello stesso punto (MondoFisico.campionaTerreno, interpolato come
 * MondoFisico.quotaSuolo). Se i due numeri non coincidono, le entita' si vedono camminare dentro
 * la terra o sospese sopra - che e' quello che e' successo il 25/10/2026, con mezzo metro di
 * differenza fra "indice della cella - 0.5" del disegno e "indice della cella" della fisica.
 *
 * La seconda verifica e' la guardia contro il tornare indietro: la VECCHIA convenzione del
 * disegno (surfaceY - 0.5) deve risultare lontana dalla mesh. Se un giorno qualcuno la rimettesse
 * credendola innocua, questa riga lo dice.
 *
 *   java -cp "&lt;classpath&gt;" com.saturno14.esit.graphics.world.SuoloSelfTest
 */
public final class SuoloSelfTest {

    private static int fatte = 0;
    private static int passate = 0;

    private SuoloSelfTest() {
    }

    private static void check(boolean ok, String cosa) {
        fatte++;
        if (ok) {
            passate++;
        } else {
            System.out.println("  FALLITA: " + cosa);
        }
    }

    public static void main(String[] args) {
        src.Simulation.setSeed(7);
        SimulationWorldBridge ponte = new SimulationWorldBridge();
        check(ponte.isSimulationReady(), "il mondo della simulazione si costruisce");
        WorldGrid grid = ponte.getGrid();
        int dim = world.getDim();

        int lato = PhysicsWorld.latoCampioni(dim);
        float[] campioni = MondoFisico.campionaTerreno(dim, lato);
        check(campioni.length == lato * lato, "un campione per nodo del campo di altezze");

        // tutti i vertici della mesh del terreno, chunk per chunk, lontano dal bordo (dove il
        // campo di altezze ripete l'ultima colonna e la mesh si chiude su una parete)
        int chunks = grid.getChunksPerAxis();
        int n = 0;
        double sommaNuova = 0, maxNuova = 0;
        double sommaVecchia = 0;
        double peggioreX = 0, peggioreZ = 0, peggioreMesh = 0, peggioreFisica = 0;
        for (int cx = 0; cx < chunks; cx++) {
            for (int cy = 0; cy < chunks; cy++) {
                for (int cz = 0; cz < chunks; cz++) {
                    MeshData m = VoxelMesher.generate(grid, cx, cy, cz);
                    if (m == null) {
                        continue;
                    }
                    for (int i = 0; i + 2 < m.positions.length; i += 3) {
                        double vx = m.positions[i], vy = m.positions[i + 1], vz = m.positions[i + 2];
                        if (vx < 1.5 || vx > dim - 2.5 || vz < 1.5 || vz > dim - 2.5) {
                            continue;
                        }
                        /*
                         * SOLO LA SUPERFICIE DI SOPRA. Il volume e' chiuso, quindi il mesher
                         * produce anche la faccia di FONDO del mondo (y = -0.5, fra la cella 0
                         * piena e il fuori vuoto): la prima stesura di questa prova la contava e
                         * misurava 10.7 m di scarto medio - un numero vero su una domanda
                         * sbagliata. La densita' e' monotona in ogni colonna, quindi oltre al
                         * fondo e alle pareti del bordo (escluse sopra) l'unica superficie e' il
                         * suolo.
                         */
                        if (vy < 1.0) {
                            continue;
                        }
                        double fisica = MondoFisico.quotaSuolo(campioni, lato, vx, vz);
                        double d = Math.abs(vy - fisica);
                        n++;
                        sommaNuova += d;
                        if (d > maxNuova) {
                            maxNuova = d;
                            peggioreX = vx;
                            peggioreZ = vz;
                            peggioreMesh = vy;
                            peggioreFisica = fisica;
                        }
                        // la vecchia convenzione del disegno: l'indice intero della cella meno 0.5
                        int cellaX = (int) Math.floor(vx), cellaZ = (int) Math.floor(vz);
                        sommaVecchia += Math.abs(vy - (world.surfaceY(cellaX, cellaZ) - 0.5));
                    }
                }
            }
        }
        check(n > 1000, "la mesh del terreno ha abbastanza vertici da misurare (" + n + ")");
        double mediaNuova = n == 0 ? Double.NaN : sommaNuova / n;
        double mediaVecchia = n == 0 ? Double.NaN : sommaVecchia / n;
        System.out.printf(Locale.ROOT,
                "suolo: %d vertici; scarto mesh-fisica medio %.3f m, massimo %.3f m "
                        + "(a x=%.2f z=%.2f: mesh %.3f, fisica %.3f)%n",
                n, mediaNuova, maxNuova, peggioreX, peggioreZ, peggioreMesh, peggioreFisica);
        System.out.printf(Locale.ROOT,
                "suolo: con la vecchia convenzione del disegno (cella - 0.5) lo scarto medio "
                        + "sarebbe %.3f m%n", mediaVecchia);
        /*
         * MEDIA SOTTO I DIECI CENTIMETRI, MASSIMO SOTTO IL MEZZO METRO. La superficie di Surface
         * Nets e' la media dei punti in cui la soglia taglia gli spigoli di un cubetto, quindi non
         * e' esattamente l'interpolazione bilineare dei nodi: sui pendii i due si scostano di
         * qualche centimetro. Mezzo metro e' invece la grandezza dell'errore che questa prova
         * esiste per impedire.
         */
        check(mediaNuova < 0.10, String.format(Locale.ROOT,
                "mesh e fisica sono la stessa superficie in media (scarto medio %.3f m)", mediaNuova));
        check(maxNuova < 0.5, String.format(Locale.ROOT,
                "nessun punto del terreno disegnato si scosta dalla fisica di mezzo metro "
                        + "(massimo %.3f m)", maxNuova));
        check(mediaVecchia > 0.3, String.format(Locale.ROOT,
                "la vecchia convenzione (cella - 0.5) era davvero lontana dalla mesh (%.3f m): "
                        + "e' la guardia contro il rimetterla", mediaVecchia));

        // la quota di una colonna piana e' esattamente quella lisciata, e i piedi degli alberi
        // stanno sulla stessa superficie
        boolean alberiOk = true;
        double scartoAlberi = 0;
        int quantiAlberi = 0;
        for (src.Flora.Tree t : src.Flora.snapshotTrees()) {
            double piede = MondoFisico.quotaSuolo(campioni, lato, t.x + 0.5, t.z + 0.5);
            double lisciata = world.quotaTerreno(t.x, t.z);
            scartoAlberi = Math.max(scartoAlberi, Math.abs(piede - lisciata));
            quantiAlberi++;
            if (Math.abs(piede - lisciata) > 0.6) {
                alberiOk = false;
            }
        }
        check(alberiOk, String.format(Locale.ROOT,
                "i piedi dei %d tronchi stanno sul suolo fisico (scarto massimo dalla colonna "
                        + "lisciata %.3f m)", quantiAlberi, scartoAlberi));

        System.out.println(passate + "/" + fatte + " verifiche superate");
        System.exit(passate == fatte ? 0 : 1);
    }
}
