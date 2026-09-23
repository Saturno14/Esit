package com.saturno14.esit.graphics.world;

import src.world;

/**
 * Ponte tra la simulazione reale (src.world, package "src" dentro 1b/) e il WorldGrid
 * usato dal motore grafico.
 *
 * Questa classe LEGGE soltanto dalla simulazione. Nessun file della simulazione viene
 * modificato: l'integrazione avviene tramite la sua API pubblica statica
 * (world.world_setup(), world.getDim(), world.check_cord_type()).
 *
 * Mapping usato:
 *   cella "Terra" -> density 1.0 (pieno, genera superficie)
 *   qualsiasi altro valore ("Air", ecc.) -> density 0.0 (vuoto)
 */
public class SimulationWorldBridge {

    private final WorldGrid grid;
    private boolean simulationReady = false;

    public SimulationWorldBridge() {
        simulationReady = world.world_setup();

        int dim = simulationReady ? world.getDim() : 20;
        this.grid = new WorldGrid(dim);

        if (simulationReady) {
            syncFromSimulation();
        } else {
            System.err.println("[SimulationWorldBridge] world_setup() ha fallito: "
                    + "uso un terreno demo come fallback.");
            grid.generateDemoTerrain();
        }
    }

    public WorldGrid getGrid() {
        return grid;
    }

    public boolean isSimulationReady() {
        return simulationReady;
    }

    /**
     * Rilegge l'intero stato della simulazione e aggiorna il WorldGrid.
     * Il WorldGrid marca se stesso come "dirty" solo quando un valore cambia davvero,
     * quindi chiamare questo metodo ad ogni frame non causa rigenerazioni inutili della mesh.
     */
    public void syncFromSimulation() {
        if (!simulationReady) return;

        int dim = grid.getDimension();
        for (int x = 0; x < dim; x++) {
            for (int y = 0; y < dim; y++) {
                for (int z = 0; z < dim; z++) {
                    float density;
                    try {
                        String cellType = world.check_cord_type(x, y, z);
                        density = "Terra".equals(cellType) ? 1.0f : 0.0f;
                    } catch (Exception e) {
                        density = 0.0f;
                    }
                    grid.setDensityIfChanged(x, y, z, density);
                }
            }
        }
    }
}
