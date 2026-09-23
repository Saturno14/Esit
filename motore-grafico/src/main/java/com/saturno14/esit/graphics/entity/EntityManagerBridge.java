package com.saturno14.esit.graphics.entity;

import src.genetics.Genome;
import src.genetics.GenomeSchema;
import src.Entity_manager;
import src.entity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Ponte in sola lettura tra Entity_manager (la simulazione) e le EntityInstance del motore
 * grafico. Crea/aggiorna/rimuove le istanze grafiche seguendo nascite, morti e crescita
 * delle entita' reali. Nessun file della simulazione viene modificato: si legge solo
 * tramite l'API pubblica (Entity_manager.Entity_count/Entity_get, entity.getX()).
 *
 * ATTENZIONE - scostamento dichiarato da GENETICS_SPEC.md (vedi risposta accompagnatoria):
 * la simulazione attuale (entity.reproduce() in 1b/src/entity.java) e' partenogenetica a un
 * solo genitore, mentre lo spec definisce un genoma diploide a DUE genitori distinti
 * (Genome.reproduce(parentA, parentB, ...)). Finche' la simulazione non espone un secondo
 * genitore ne' un id di parentela, questo bridge:
 *   1) individua il genitore per euristica di posizione (il figlio nasce sulla stessa
 *      cella del genitore, allo stesso tick — cosi' come fa entity.reproduce());
 *   2) chiama Genome.reproduce(parent, parent, ...): l'unico genitore gioca sia il ruolo
 *      di parentA che di parentB. Il crossover/mutazione/lineage restano quelli definiti
 *      dallo spec, ma la diversita' genetica e' inferiore a quella di una vera
 *      fecondazione a due individui distinti (i due "genitori" hanno esattamente gli
 *      stessi alleli di partenza). Segnalalo se preferisci un comportamento diverso
 *      (es. genoma sempre casuale per i figli, in attesa di riproduzione sessuata reale).
 */
public class EntityManagerBridge {

    private final GenomeSchema schema;
    private final Random rng = new Random();
    private final Map<Integer, EntityInstance> instances = new HashMap<>();
    private long tickCounter = 0;

    public EntityManagerBridge(GenomeSchema schema) {
        this.schema = schema;
    }

    /** Da chiamare una volta per frame: sincronizza le istanze grafiche con lo stato reale. */
    public void sync() {
        tickCounter++;
        int count = Entity_manager.Entity_count();
        Set<Integer> aliveIds = new HashSet<>();
        Map<Integer, EntityInstance> previousInstances = new HashMap<>(instances);

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
                Genome genome = createGenomeForBirth(id, e.getPos(), previousInstances);
                instance = new EntityInstance(id, genome, schema, e.getSex(), e.getAge(), e.getPos());
                instances.put(id, instance);
            } else {
                instance.update(e.getAge(), e.getPos(), schema);
            }
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

    private Genome createGenomeForBirth(int id, int[] pos, Map<Integer, EntityInstance> candidates) {
        for (EntityInstance candidate : candidates.values()) {
            if (Arrays.equals(candidate.getPosition(), pos)) {
                Genome parent = candidate.genome;
                return Genome.reproduce(parent, parent, schema, rng, String.valueOf(id), tickCounter);
            }
        }
        // Nessun genitore trovato per euristica (entita' fondatrice o avvio simulazione)
        return Genome.random(schema, rng, String.valueOf(id), tickCounter);
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
