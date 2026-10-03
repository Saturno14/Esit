package src.genetics;

/**
 * Fenotipo finale delle statistiche vitali (GENETICS_SPEC.md punto 10). Costruito una sola
 * volta alla nascita via PhenotypeResolver e cachato: non calcola nulla da solo, non muta
 * mai dopo la creazione (il genoma stesso e' immutabile).
 */
public final class EntityStats {

    public final double maxHealth;
    public final double sightDistance;
    public final double speed;
    public final double maxStamina;
    public final double foodAbsorption;
    public final double attackPower;
    public final DietType diet;

    private EntityStats(double maxHealth, double sightDistance, double speed,
                         double maxStamina, double foodAbsorption, double attackPower,
                         DietType diet) {
        this.maxHealth = maxHealth;
        this.sightDistance = sightDistance;
        this.speed = speed;
        this.maxStamina = maxStamina;
        this.foodAbsorption = foodAbsorption;
        this.attackPower = attackPower;
        this.diet = diet;
    }

    public static EntityStats resolve(Genome genome, GenomeSchema schema) {
        double maxHealth = PhenotypeResolver.resolve("stat.max_health", genome, schema);
        double sightDistance = PhenotypeResolver.resolve("stat.sight_distance", genome, schema);
        double speed = PhenotypeResolver.resolve("stat.base_speed", genome, schema);
        double maxStamina = PhenotypeResolver.resolve("stat.max_stamina", genome, schema);
        double foodAbsorption = PhenotypeResolver.resolve("stat.food_absorption", genome, schema);
        double attackPower = PhenotypeResolver.resolve("stat.attack_power", genome, schema);
        double dietBias = PhenotypeResolver.resolve("behavior.diet_bias", genome, schema);

        return new EntityStats(maxHealth, sightDistance, speed, maxStamina, foodAbsorption,
                attackPower, DietType.fromResolvedBias(dietBias));
    }
}
