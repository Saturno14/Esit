package com.saturno14.esit.graphics.entity;

import src.genetics.GenomeSchema;
import src.Entity_manager;
import src.Corpse;
import src.Simulation;
import src.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Ponte in sola lettura tra Entity_manager (la simulazione) e le EntityInstance del motore
 * grafico. Crea/aggiorna/rimuove le istanze grafiche seguendo nascite, morti, crescita e
 * variazioni del genoma delle entita' reali.
 *
 * Il genoma di ogni entita' vive nella simulazione (entity.getGenome()): qui si legge
 * soltanto, non se ne crea piu' uno "grafico" a parte. Nessun file della simulazione viene
 * modificato dal bridge.
 */
public class EntityManagerBridge {

    private final GenomeSchema schema;
    private final Map<Integer, EntityInstance> instances = new HashMap<>();

    public EntityManagerBridge(GenomeSchema schema) {
        this.schema = schema;
    }

    /** Da chiamare una volta per frame: sincronizza le istanze grafiche con lo stato reale. */
    public void sync() {
        int count = Entity_manager.Entity_count();
        Set<Integer> aliveIds = new HashSet<>();

        for (int i = 0; i < count; i++) {
            entity e;
            try {
                e = Entity_manager.Entity_get(i);
            } catch (Exception ex) {
                continue; // indice non piu' valido (es. lista cambiata durante l'iterazione): salta
            }
            if (e == null || !e.isAlive()) continue;

            int id = e.getId();
            aliveIds.add(id);

            EntityInstance instance = instances.get(id);
            if (instance == null) {
                instance = new EntityInstance(id, e.getGenome(), schema, e.getSex(), e.getAge(), e.getPos());
                instances.put(id, instance);
            } else {
                instance.update(e.getAge(), e.getPos(), e.getGenome(), schema);
            }
        }

        // Cadaveri: restano visibili (sdraiati) finche' esistono nella simulazione
        for (Corpse corpse : Simulation.corpsesSnapshot()) {
            entity body = corpse.getBody();
            int id = body.getId();
            aliveIds.add(id);
            EntityInstance instance = instances.get(id);
            if (instance == null) {
                instance = new EntityInstance(id, body.getGenome(), schema, body.getSex(),
                        body.getAge(), body.getPos());
                instances.put(id, instance);
            }
            instance.setDead(true);
        }

        // Rimuovi le istanze di entita' non piu' vive (morte), liberando le risorse GPU
        instances.keySet().removeIf(id -> {
            if (!aliveIds.contains(id)) {
                instances.get(id).destroy();
                return true;
            }
            return false;
        });
    }

    public Iterable<EntityInstance> getInstances() {
        return instances.values();
    }

    /** Libera tutte le mesh GPU delle entita' correnti. Chiamala alla chiusura della finestra. */
    public void destroyAll() {
        for (EntityInstance instance : instances.values()) {
            instance.destroy();
        }
        instances.clear();
    }
}
