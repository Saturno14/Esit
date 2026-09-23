package com.saturno14.esit.graphics.entity;

import src.genetics.EntityStats;
import src.genetics.Genome;
import src.genetics.GenomeSchema;
import com.saturno14.esit.graphics.mesh.Mesh;
import org.joml.Vector3f;

/**
 * Stato grafico associato a una singola entita' viva: mesh caricata sulla GPU, colore,
 * posizione corrente, genoma e fenotipo (EntityStats) risolto una sola volta alla nascita
 * e cachato (GENETICS_SPEC.md punto 3: il genoma e' immutabile dopo la creazione).
 */
public class EntityInstance {

    public final int entityId;
    public final Genome genome;
    public final EntityStats stats;
    public final Vector3f color;

    private final Mesh mesh;
    private int lastAge;
    private int[] position;

    public EntityInstance(int entityId, Genome genome, GenomeSchema schema, int sex,
                           int initialAge, int[] initialPosition) {
        this.entityId = entityId;
        this.genome = genome;
        this.stats = EntityStats.resolve(genome, schema);
        this.color = GenomeMeshApplier.colorFor(genome, schema, sex);
        this.mesh = new Mesh(GenomeMeshApplier.generateBody(genome, schema, initialAge));
        this.lastAge = initialAge;
        this.position = initialPosition;
    }

    /** Da chiamare una volta per frame: aggiorna posizione sempre, rigenera la mesh solo se l'eta' e' cambiata. */
    public void update(int currentAge, int[] currentPosition, GenomeSchema schema) {
        if (currentAge != lastAge) {
            mesh.upload(GenomeMeshApplier.generateBody(genome, schema, currentAge));
            lastAge = currentAge;
        }
        this.position = currentPosition;
    }

    public int[] getPosition() {
        return position;
    }

    public Mesh getMesh() {
        return mesh;
    }

    public void destroy() {
        mesh.destroy();
    }
}
