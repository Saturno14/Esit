package com.saturno14.esit.graphics.entity;

import src.genetics.EntityStats;
import src.genetics.Genome;
import src.genetics.GenomeSchema;
import com.saturno14.esit.graphics.mesh.Mesh;
import org.joml.Vector3f;

/**
 * Stato grafico associato a una singola entita' viva: mesh caricata sulla GPU, colore,
 * posizione corrente, genoma e fenotipo (EntityStats).
 *
 * Il genoma NON nasce piu' qui: appartiene all'entita' della simulazione (entity.getGenome())
 * e questa istanza si limita a rispecchiarlo. Quando la simulazione sostituisce il genoma
 * (deriva legata all'eta') o l'eta' cambia, mesh, colore e stat vengono ricalcolati.
 */
public class EntityInstance {

    public final int entityId;
    public final Vector3f color = new Vector3f();

    private final Mesh mesh;
    private final int sex;
    private Genome genome;
    private EntityStats stats;
    private int lastAge;
    private int[] position;
    private boolean dead;

    public EntityInstance(int entityId, Genome genome, GenomeSchema schema, int sex,
                           int initialAge, int[] initialPosition) {
        this.entityId = entityId;
        this.sex = sex;
        this.genome = genome;
        this.stats = EntityStats.resolve(genome, schema);
        this.color.set(GenomeMeshApplier.colorFor(genome, schema, sex));
        this.mesh = new Mesh(GenomeMeshApplier.generateBody(genome, schema, initialAge));
        this.lastAge = initialAge;
        this.position = initialPosition;
    }

    /**
     * Da chiamare una volta per frame: aggiorna la posizione sempre, rigenera la mesh solo se
     * l'eta' o il genoma dell'entita' sono cambiati.
     */
    public void update(int currentAge, int[] currentPosition, Genome currentGenome, GenomeSchema schema) {
        boolean genomeChanged = currentGenome != genome;
        if (genomeChanged) {
            genome = currentGenome;
            stats = EntityStats.resolve(genome, schema);
            color.set(GenomeMeshApplier.colorFor(genome, schema, sex));
        }
        if (genomeChanged || currentAge != lastAge) {
            mesh.upload(GenomeMeshApplier.generateBody(genome, schema, currentAge));
            lastAge = currentAge;
        }
        this.position = currentPosition;
    }

    public Genome getGenome() {
        return genome;
    }

    /** True per un cadavere: il Renderer lo disegna sdraiato e scurito. */
    public boolean isDead() {
        return dead;
    }

    public void setDead(boolean dead) {
        this.dead = dead;
    }

    public EntityStats getStats() {
        return stats;
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
