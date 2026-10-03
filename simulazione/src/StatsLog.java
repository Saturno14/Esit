package src;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

import src.genetics.DietType;
import src.genetics.EntityStats;

/**
 * Log CSV dell'evoluzione: una riga ogni pochi tick con popolazione, risorse, nascite, morti
 * (per fame e per combattimento) e medie dei geni. Serve a capire se l'evoluzione sta
 * funzionando e a tarare i parametri. File: logs/stats-session-N.csv
 */
public final class StatsLog {

    private static final Path DIR = Path.of("logs");
    private static Path file;

    private StatsLog() {
    }

    /** Apre un nuovo file per la sessione corrente (da chiamare a ogni NewStart). */
    public static synchronized void open(int session) {
        try {
            Files.createDirectories(DIR);
            file = DIR.resolve("stats-session-" + session + ".csv");
            if (!Files.exists(file)) {
            Files.writeString(file,
                    "tick,population,apples,corpses,births,deaths_starved,deaths_killed,"
                            + "avg_generation,max_generation,avg_health,avg_speed,avg_sight,avg_stamina,"
                            + "avg_attack,herbivores,omnivores,carnivores,avg_fitness\n");
            }
        } catch (IOException e) {
            System.out.println("Errore apertura log statistiche: " + e.getMessage());
            file = null;
        }
    }

    public static synchronized void record(long tick, int apples, int corpses, int births,
                                            int deathsStarved, int deathsKilled) {
        if (file == null) {
            return;
        }
        List<entity> alive = Entity_manager.snapshotAlive();
        int n = alive.size();
        double gen = 0, maxGen = 0, health = 0, speed = 0, sight = 0, stamina = 0, attack = 0, fitness = 0;
        int herb = 0, omni = 0, carn = 0;
        for (entity e : alive) {
            EntityStats s = e.getStats();
            int g = e.getGenome().lineage().generation();
            gen += g;
            maxGen = Math.max(maxGen, g);
            health += s.maxHealth;
            speed += s.speed;
            sight += s.sightDistance;
            stamina += s.maxStamina;
            attack += s.attackPower;
            fitness += e.getFitness();
            if (s.diet == DietType.HERBIVORE) herb++;
            else if (s.diet == DietType.OMNIVORE) omni++;
            else carn++;
        }
        double d = Math.max(1, n);
        String line = String.format(Locale.ROOT,
                "%d,%d,%d,%d,%d,%d,%d,%.2f,%d,%.1f,%.2f,%.2f,%.1f,%.1f,%d,%d,%d,%.3f%n",
                tick, n, apples, corpses, births, deathsStarved, deathsKilled,
                gen / d, (int) maxGen, health / d, speed / d, sight / d, stamina / d, attack / d,
                herb, omni, carn, fitness / d);
        try {
            Files.writeString(file, line, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("Errore scrittura log statistiche: " + e.getMessage());
        }
    }
}
