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
                            + "avg_attack,herbivores,omnivores,carnivores,avg_fitness,avg_fov,avg_nightvision,daylight,"
                            + "males,females,mature_males,mature_females,deaths_m,deaths_f,"
                            // il cibo e la sete non erano registrati: si ragionava sulla fame
                            // senza averne una misura. avg_diet_bias dice dove sta andando
                            // l'abitudine alimentare della popolazione (0 vegetale, 1 carne)
                            + "avg_food,avg_thirst,avg_diet_bias\n");
            }
        } catch (IOException e) {
            System.out.println("Errore apertura log statistiche: " + e.getMessage());
            file = null;
        }
    }

    public static synchronized void record(long tick, int apples, int corpses, int births,
                                            int deathsStarved, int deathsKilled,
                                            int deathsMale, int deathsFemale) {
        if (file == null) {
            return;
        }
        List<entity> alive = Entity_manager.snapshotAlive();
        int n = alive.size();
        double gen = 0, maxGen = 0, health = 0, speed = 0, sight = 0, stamina = 0, attack = 0, fitness = 0;
        double fov = 0, night = 0, dietBias = 0, cibo = 0, sete = 0;
        int herb = 0, omni = 0, carn = 0;
        // i sessi, e quanti di questi sono in eta' da riproduzione: e' il secondo numero quello
        // che decide se possono ancora nascere figli
        int maschi = 0, femmine = 0, maschiMaturi = 0, femmineMature = 0;
        for (entity e : alive) {
            EntityStats s = e.getStats();
            int g = e.getGenome().lineage().generation();
            gen += g;
            maxGen = Math.max(maxGen, g);
            health += s.maxHealth;
            speed += s.speed;
            sight += s.sightDistance;
            fov += s.fieldOfView;
            night += s.nightVision;
            stamina += s.maxStamina;
            attack += s.attackPower;
            fitness += e.getFitness();
            // l'etichetta CORRENTE, non quella di nascita: la dieta cambia nella vita
            if (e.dietNow() == DietType.HERBIVORE) herb++;
            else if (e.dietNow() == DietType.OMNIVORE) omni++;
            else carn++;
            dietBias += e.getDietBias();
            cibo += e.getFood();
            sete += e.getThirst();
            if (e.getSex() == 2) {
                femmine++;
                if (e.isMature()) femmineMature++;
            } else {
                maschi++;
                if (e.isMature()) maschiMaturi++;
            }
        }
        double d = Math.max(1, n);
        String line = String.format(Locale.ROOT,
                "%d,%d,%d,%d,%d,%d,%d,%.2f,%d,%.1f,%.2f,%.2f,%.1f,%.1f,%d,%d,%d,%.3f,%.0f,%.3f,%.2f,"
                        + "%d,%d,%d,%d,%d,%d,%.1f,%.1f,%.3f%n",
                tick, n, apples, corpses, births, deathsStarved, deathsKilled,
                gen / d, (int) maxGen, health / d, speed / d, sight / d, stamina / d, attack / d,
                herb, omni, carn, fitness / d, fov / d, night / d, DayNight.daylight(tick),
                maschi, femmine, maschiMaturi, femmineMature, deathsMale, deathsFemale,
                cibo / d, sete / d, dietBias / d);
        try {
            Files.writeString(file, line, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("Errore scrittura log statistiche: " + e.getMessage());
        }
    }
}
