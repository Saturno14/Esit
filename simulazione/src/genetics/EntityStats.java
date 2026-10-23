package src.genetics;

/**
 * Fenotipo finale delle statistiche vitali (GENETICS_SPEC.md punto 10). Costruito una sola
 * volta alla nascita via PhenotypeResolver e cachato: non calcola nulla da solo, non muta
 * mai dopo la creazione (il genoma stesso e' immutabile).
 */
public final class EntityStats {

    public final double maxHealth;
    public final double sightDistance;
    /**
     * Ampiezza del cono di visuale in GRADI (vedi Vision): l'entita' vede solo dentro il cono
     * centrato dove guarda. Non e' gratis in nessuno dei due versi - un cono largo vede piu'
     * mondo ma con meno risoluzione angolare, perche' gli 8 settori della retina si spartiscono
     * il cono: occhi larghi da preda o occhi stretti da predatore sono un compromesso vero, e lo
     * decide l'evoluzione.
     */
    public final double fieldOfView;
    /**
     * Quanto si vede di notte, da 0 (ci si vede un quarto) a 1 (come di giorno). Vedi
     * src.DayNight.visibility: e' il gene che decide se il buio e' un problema.
     */
    public final double nightVision;
    public final double speed;
    public final double maxStamina;
    public final double foodAbsorption;
    public final double attackPower;
    /**
     * PREDISPOSIZIONE alla dieta, da 0 (vegetale) a 1 (carne): e' il valore del gene, cioe' il
     * punto da cui la dieta PARTE, non quello in cui resta. La dieta vera e' uno stato di vita
     * che si sposta a ogni pasto verso cio' che si e' mangiato (vedi entity.dietBias e
     * entity.adattaDieta): quello che si eredita e' il gusto iniziale, non l'abitudine.
     */
    public final double dietBias;
    /** L'etichetta alla NASCITA. Quella corrente la da' entity.dietNow(), e cambia nella vita. */
    public final DietType diet;

    private EntityStats(double maxHealth, double sightDistance, double fieldOfView,
                         double nightVision, double speed,
                         double maxStamina, double foodAbsorption, double attackPower,
                         double dietBias, DietType diet) {
        this.maxHealth = maxHealth;
        this.sightDistance = sightDistance;
        this.fieldOfView = fieldOfView;
        this.nightVision = nightVision;
        this.speed = speed;
        this.maxStamina = maxStamina;
        this.foodAbsorption = foodAbsorption;
        this.attackPower = attackPower;
        this.dietBias = dietBias;
        this.diet = diet;
    }

    public static EntityStats resolve(Genome genome, GenomeSchema schema) {
        double maxHealth = PhenotypeResolver.resolve("stat.max_health", genome, schema);
        double sightDistance = PhenotypeResolver.resolve("stat.sight_distance", genome, schema);
        double fieldOfView = PhenotypeResolver.resolve("stat.field_of_view", genome, schema);
        double nightVision = PhenotypeResolver.resolve("stat.night_vision", genome, schema);
        double speed = PhenotypeResolver.resolve("stat.base_speed", genome, schema);
        double maxStamina = PhenotypeResolver.resolve("stat.max_stamina", genome, schema);
        double foodAbsorption = PhenotypeResolver.resolve("stat.food_absorption", genome, schema);
        double attackPower = PhenotypeResolver.resolve("stat.attack_power", genome, schema);
        double dietBias = PhenotypeResolver.resolve("behavior.diet_bias", genome, schema);

        return new EntityStats(maxHealth, sightDistance, fieldOfView, nightVision, speed,
                maxStamina, foodAbsorption, attackPower,
                dietBias, DietType.fromResolvedBias(dietBias));
    }
}
