package com.saturno14.esit.graphics.entity;

import src.genetics.EntityStats;
import src.genetics.Genome;
import src.genetics.GenomeSchema;
import com.saturno14.esit.graphics.mesh.Mesh;
import org.joml.Vector3f;
import src.body.BodyPose;

/**
 * Stato grafico associato a una singola entita' viva: mesh caricata sulla GPU, colore,
 * posizione corrente, genoma e fenotipo (EntityStats).
 *
 * Il genoma NON nasce piu' qui: appartiene all'entita' della simulazione (entity.getGenome())
 * e questa istanza si limita a rispecchiarlo. Quando la simulazione sostituisce il genoma
 * (deriva legata all'eta') o l'eta' cambia, colore e stat vengono ricalcolati.
 *
 * CORPO. Il disegno non usa piu' una mesh per entita': si chiede all'entita' la posa dei suoi
 * segmenti ({@link src.entity#poseLocal}) e la si disegna pezzo per pezzo (PoseRenderer). Cosi'
 * l'andatura si vede, e cambiare eta' o morfologia non costa nessuna rigenerazione di mesh. La
 * vecchia mesh unica (GenomeMeshApplier, Surface Nets) resta disponibile con {@link #SKELETON}
 * a false.
 */
public class EntityInstance {

    public final int entityId;
    public final Vector3f color = new Vector3f();

    /** false = vecchio disegno a mesh unica rigenerata (Surface Nets). */
    public static volatile boolean SKELETON = true;

    /** Tempo in cui il disegno recupera uno scatto della simulazione (s). */
    private static final float SMOOTHING_SECONDS = 0.12f;

    private final Mesh mesh;
    private final src.entity source;
    private float renderX;
    private float renderZ;
    private boolean renderPlaced;
    private final int sex;
    private Genome genome;
    private EntityStats stats;
    private int lastAge;
    private int[] position;
    private boolean dead;

    public EntityInstance(int entityId, src.entity source, Genome genome, GenomeSchema schema, int sex,
                           int initialAge, int[] initialPosition) {
        this.entityId = entityId;
        this.source = source;
        this.sex = sex;
        this.genome = genome;
        this.stats = EntityStats.resolve(genome, schema);
        this.color.set(GenomeMeshApplier.colorFor(genome, schema, sex));
        this.mesh = SKELETON ? null : new Mesh(GenomeMeshApplier.generateBody(genome, schema, initialAge));
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
        if (mesh != null && (genomeChanged || currentAge != lastAge)) {
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

    /** L'entita' della simulazione a cui questa istanza si riferisce (sola lettura). */
    public src.entity getSource() {
        return source;
    }

    /**
     * Posa corrente dei segmenti, nel sistema locale dell'entita' (origine sotto i piedi).
     *
     * @param extraSeconds secondi da aggiungere alla fase dell'andatura, per interpolare tra due
     *                     tick della simulazione e vedere un movimento fluido
     */
    public BodyPose pose(double extraSeconds) {
        return source.poseLocal(extraSeconds);
    }

    /** Posizione continua in metri: tra un tick e l'altro l'entita' sta tra due celle. */
    public float exactX() {
        return (float) source.getExactX();
    }

    public float exactZ() {
        return (float) source.getExactZ();
    }

    /**
     * Posizione addolcita per il disegno: la simulazione sposta l'entita' a scatti (un passo per
     * azione), qui la si insegue con un'interpolazione, cosi' non teletrasporta tra due celle.
     * Da chiamare una volta per frame.
     *
     * @param deltaTime secondi di tempo reale dall'ultimo frame
     */
    public void advanceRenderPosition(float deltaTime) {
        float tx = exactX();
        float tz = exactZ();
        if (!renderPlaced) {
            renderX = tx;
            renderZ = tz;
            renderPlaced = true;
            return;
        }
        float k = Math.min(1f, deltaTime / SMOOTHING_SECONDS);
        renderX += (tx - renderX) * k;
        renderZ += (tz - renderZ) * k;
    }

    public float renderX() {
        return renderX;
    }

    public float renderZ() {
        return renderZ;
    }

    public void destroy() {
        if (mesh != null) {
            mesh.destroy();
        }
    }
}
