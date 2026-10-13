package src;

/**
 * Bilanciamento delle azioni e degli istinti in un unico posto. Tre profili confrontabili:
 *
 *  - {@link #legacy()}: i valori originali (compreso il cablaggio sfasato degli istinti: vedi STATO_LAVORO.txt).
 *  - {@link #balanced()}: legacy con mangiare/riproduzione piu' premiati e sonno meno (effetto nullo misurato).
 *  - {@link #structured()}: IN USO. Azioni mascherate (si sceglie solo tra quelle che possono riuscire),
 *    istinti graduati con il cablaggio corretto, ingresso "acqua adiacente" e ricompensa del bere
 *    proporzionale alla sete; gli attacchi sono sempre possibili (con stamina), anche senza nessuno vicino.
 *
 * I campi sono volatili e modificabili anche da src.Experiment (es. maskActions=0).
 */
public final class ActionWeights {

    // ---- ricompense (Netreward)
    public static volatile double eatApple;
    public static volatile double eatCorpse;          // per morso intero di carogna (scala col morso)
    public static volatile double reproduceSelf;
    public static volatile double reproducePartner;
    public static volatile double sleepNeedFactor;    // dormire stanchi: ricompensa = need * fattore
    public static volatile double sleepRestedPenalty; // dormire da riposati (negativo)
    public static volatile double drinkReward;        // bere: ricompensa
    public static volatile double drinkNeedScaled;    // 1 = ricompensa * (1 - sete/MAX), 0 = fissa

    // ---- istinti legacy (seedVersion < 3)
    public static volatile double eatNeuronWeight;
    public static volatile double mateBias;
    public static volatile double restBias;

    // ---- struttura
    public static volatile double seedVersion;        // 3 = istinti graduati (cablaggio corretto); <3 = originali
    public static volatile double maskActions;        // 1 = scelta solo tra azioni che possono riuscire
    public static volatile double hereWaterAdj;       // 1 = ingresso "qui acqua" vero anche per celle adiacenti
    public static volatile double allowAttack;        // 0 = con la maschera attivata, ATTACK non e' mai scelta (per esperimenti)

    // ---- istinti v3 (spinte graduate, pre-attivazione dello strato 1)
    public static volatile double n_moveGain, n_moveFood, n_moveThirst, n_moveBias, n_moveHereWater;
    public static volatile double n_eatGain, n_eatFood, n_eatBias;
    public static volatile double n_drinkGain, n_drinkHere, n_drinkThirst, n_drinkBias;
    public static volatile double n_restGain, n_restBias;
    public static volatile double n_mateFood, n_mateMature, n_mateEntity, n_mateBias;
    public static volatile double n_idleBias;         // bias delle uscite IDLE e ATTACK (quasi spente)

    static {
        structured();
    }

    private ActionWeights() {
    }

    private static void rewardsLegacy() {
        eatApple = 3.0;
        eatCorpse = 4.0;
        reproduceSelf = 20.0;
        reproducePartner = 10.0;
        sleepNeedFactor = 1.0;
        sleepRestedPenalty = -0.5;
        drinkReward = 3.0;
        drinkNeedScaled = 0;
        eatNeuronWeight = 8.0;
        mateBias = -9.0;
        restBias = 3.0;
    }

    private static void v3Parameters() {
        n_moveGain = 3; n_moveFood = -1.5; n_moveThirst = -1.5; n_moveBias = 0.5; n_moveHereWater = -6;
        n_eatGain = 6; n_eatFood = -3; n_eatBias = -1;
        n_drinkGain = 0; n_drinkHere = 6; n_drinkThirst = -6; n_drinkBias = -1.5;
        n_restGain = -6; n_restBias = 2.5;
        n_mateFood = 2; n_mateMature = 2; n_mateEntity = 3; n_mateBias = -5.5;
        n_idleBias = -3;
    }

    /** Valori originali, istinti sfasati compresi. */
    public static void legacy() {
        rewardsLegacy();
        seedVersion = 0;
        maskActions = 0;
        allowAttack = 1;
        hereWaterAdj = 0;
        v3Parameters();
    }

    /** Legacy con ricompense di mangiare e riproduzione maggiori e sonno minore (nessun effetto misurabile). */
    public static void balanced() {
        legacy();
        eatApple = 6.0;
        eatCorpse = 8.0;
        reproduceSelf = 40.0;
        reproducePartner = 20.0;
        sleepNeedFactor = 0.5;
        sleepRestedPenalty = -1.0;
        eatNeuronWeight = 12.0;
        restBias = 1.5;
    }

    /** Maschera delle azioni + istinti graduati + acqua adiacente + bere guidato dal bisogno. */
    public static void structured() {
        rewardsLegacy();
        drinkReward = 6.0;
        drinkNeedScaled = 1;
        seedVersion = 3;
        maskActions = 1;
        allowAttack = 1;
        hereWaterAdj = 1;
        v3Parameters();
    }
}
