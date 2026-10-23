package src;

/**
 * Bilanciamento delle azioni e degli istinti in un unico posto. Tre profili confrontabili:
 *
 *  - {@link #legacy()}: i valori originali (compreso il cablaggio sfasato degli istinti: vedi STATO_LAVORO.txt).
 *  - {@link #balanced()}: legacy con mangiare/riproduzione piu' premiati e sonno meno (effetto nullo misurato).
 *  - {@link #embodied()}: IN USO. structured() piu' gli effetti del corpo (massa, portata, andatura
 *    misurata con la fisica): vedi i campi body*.
 *  - {@link #structured()}: azioni mascherate (si sceglie solo tra quelle che possono riuscire),
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
    /**
     * Resta per i log e per i confronti di taratura, ma NON seleziona piu' un cablaggio: con la
     * vista a cono e le azioni egocentriche ce n'e' uno solo (istinti v4, vedi
     * entity.seedInstincts). I cablaggi vecchi collegavano "settore a nord-est" a "muoviti verso
     * est", e ne' quei settori ne' quelle azioni esistono piu'.
     */
    public static volatile double seedVersion;
    public static volatile double maskActions;        // 1 = scelta solo tra azioni che possono riuscire
    public static volatile double allowAttack;        // 0 = con la maschera attivata, ATTACK non e' mai scelta (per esperimenti)
    /**
     * LA DIETA PLASTICA (vedi entity.dietBias). Non c'e' piu' nessun divieto: chiunque mangia
     * qualunque cosa, ma ogni pasto sposta l'abitudine verso cio' che si e' mangiato, e piu'
     * l'abitudine e' sbilanciata piu' quel cibo sazia.
     *
     * STORIA. Prima la dieta erano tre scatole decise alla nascita con rese fisse, e il morso non
     * le guardava nemmeno: la #10 della sessione 19 era carnivora e aveva mangiato 277 mele. Il
     * primo rimedio fu un divieto (il vecchio interruttore dietFood). Un divieto pero' non e' una
     * pressione selettiva, e' un muro: non costa niente a nessuno e non si puo' sbagliare. Il
     * gradiente lo sostituisce - specializzarsi rende, e rende anche fragili.
     *
     * dietAdapt: quanto un singolo pasto avvicina l'abitudine al cibo mangiato. A 0.02 servono
     * circa 35 pasti per dimezzare la distanza, cioe' una specializzazione e' il lavoro di una
     * vita e non di un pranzo. A 0 la dieta non cambia mai: e' il braccio di controllo.
     *
     * dietEffMin/Max: la resa del cibo da "non e' il mio cibo" a "e' esattamente il mio cibo".
     * Con 0.25 e 1.35 il punto centrale e' 0.80, identico all'onnivoro di prima: la media del
     * mondo non si sposta, si allargano solo gli estremi.
     */
    /**
     * IL PREMIO DAL CORPO. needPain e' quanto costa ogni passo per ogni unita' di bisogno (fame
     * piu' sete, ciascuna da 0 a 1): a 0.5 un corpo completamente a secco paga 1 per passo,
     * cioe' la stessa scala del vecchio "+0.5 per un passo sopravvissuto" che ha sostituito.
     * E' un segnale denso che viene dal proprio stato e non indica nessuna direzione.
     *
     * windowDecay e' quanto la soglia dell'apprendimento in vita scende verso il punteggio
     * recente quando una mutazione viene rifiutata: serve a non congelare il cervello su una
     * finestra fortunata (vedi entity.step).
     */
    public static volatile double needPain;
    public static volatile double windowDecay;

    public static volatile double dietAdapt;
    public static volatile double dietEffMin;
    public static volatile double dietEffMax;

    // ---- il corpo conta (morfologia -> sopravvivenza). Tutti a 1 in structured(), a 0 nei profili vecchi.
    public static volatile double bodyGaitSpeed;      // 1 = quanto avanza un passo viene dalla fisica dell'andatura
    public static volatile double bodyMassStats;      // 1 = la massa pesa su salute e costo del movimento
    public static volatile double bodyReach;          // 1 = portata delle estremita' per mangiare e attaccare
    public static volatile double bodyAttackPower;    // 1 = massa e portata entrano nel danno

    // ---- economia del mondo (vedi STATO_LAVORO.txt, voce 12 del 23/10/2026). Attive in tutti i
    // profili: non sono effetti del corpo ma correzioni al mondo. Si spengono per i confronti A/B
    // (src.Experiment ... "timeMetabolism=0").
    /**
     * 1 = fame e sete scendono col TEMPO (per tick) e un passo costa in proporzione alla strada
     * che copre; 0 = il vecchio consumo per AZIONE.
     *
     * PERCHE'. Con il consumo per azione un'entita' lenta ha un metabolismo lento in tempo di
     * mondo, perche' le azioni arrivano al ritmo speed/7. Misurato nella sessione 13: la
     * velocita' media della popolazione e' scesa da 1.80 a 1.05 e l'ultima sopravvissuta (la 120)
     * ha vissuto 27000 tick facendo 3672 azioni e mangiando 33 mele. Non aveva imparato a
     * sopravvivere: aveva imparato a non fare niente, piano. Finche' il tempo non costa, la
     * strategia vincente e' la torpore e tutto il lavoro sulla locomozione non conta.
     */
    public static volatile double timeMetabolism;
    /**
     * Dislivello in celle entro cui si vede, si raggiunge e si trova un partner. 0 = solo la
     * propria quota, come prima; 1 = un gradino.
     *
     * PERCHE'. Vision.sense, appleWithinReach e la ricerca del partner filtravano sulla quota
     * esatta dell'entita'. Le mele della sessione 13 stavano su quattro quote: una mela a una
     * cella di distanza ma su un gradino diverso non esisteva, e due entita' su quote diverse
     * erano mutuamente invisibili per sempre. Risultato: 90 morti di fame su 126 con le mele
     * SEMPRE al tetto, generazioni dimezzate a ogni passo (63, 35, 15, 7, 5, 1) e zero nascite
     * dopo il tick 8000 con sette entita' ancora vive su 2500 colonne. Il movimento, invece, i
     * gradini li ha sempre attraversati (syncCell segue l'altezza del terreno): era solo la
     * percezione a non farlo.
     */
    public static volatile double levelTolerance;

    // ---- istinti v3 (spinte graduate, pre-attivazione dello strato 1)
    public static volatile double n_moveGain, n_moveFood, n_moveThirst, n_moveBias, n_moveHereWater;
    /**
     * Peso di "non vedo niente" sul neurone del passo avanti: POSITIVO, cioe' l'ignoranza spinge
     * a esplorare. Deve restare maggiore di n_turnNothing, altrimenti si torna a girare sul posto.
     */
    public static volatile double n_moveNothing;
    /** Spinta a girare la TESTA quando il cono e' vuoto, e a girare il CORPO come ripiego. */
    public static volatile double n_scanGain, n_scanBias, n_turnNothing;
    /**
     * Freno sulle rotazioni per chi e' dissetato: NEGATIVO. Serve a non far guidare le rotazioni
     * dall'acqua quando l'acqua non serve - vedi entity.turnNeuron, dove un lago visto di lato
     * produceva un'oscillazione perpetua.
     */
    public static volatile double n_turnSated;
    public static volatile double n_eatGain, n_eatFood, n_eatBias;
    public static volatile double n_drinkGain, n_drinkHere, n_drinkThirst, n_drinkBias;
    public static volatile double n_restGain, n_restBias;
    public static volatile double n_mateFood, n_mateMature, n_mateEntity, n_mateBias;
    public static volatile double n_idleBias;         // bias delle uscite IDLE e ATTACK (quasi spente)

    static {
        embodied();
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
        timeMetabolism = 1;
        levelTolerance = 1;
    }

    /** Effetti del corpo spenti: comportamento identico a prima che la morfologia contasse. */
    private static void bodyEffectsOff() {
        bodyGaitSpeed = 0;
        bodyMassStats = 0;
        bodyReach = 0;
        bodyAttackPower = 0;
    }

    /**
     * Taratura della scansione (istinti v4). La spinta a girare la testa e' FORTE perche' con la
     * vista a cono "non vedo niente" e' lo stato di partenza di ogni entita' appena nata: se non
     * guarda intorno non trova niente e muore senza aver mai provato. Il corpo si gira con una
     * spinta piu' debole: e' il ripiego per quando il collo e' a fondo corsa, e in quel momento
     * l'azione della testa non e' piu' possibile, quindi la maschera delle azioni la esclude da
     * sola (vedi entity.feasibleActions).
     */
    private static void v4Parameters() {
        n_scanGain = 6.0;
        /*
          * La soglia e' sotto a quanto il solo termine del collo (n_scanGain/3 = 2.0) puo'
          * produrre: altrimenti i due neuroni di scansione si accendono anche con il cono PIENO,
          * guidati dalla sola propriocezione - gira a destra, resta meno collo a destra e piu' a
          * sinistra, gira a sinistra - e la testa oscilla per tutta la vita invece di scandagliare
          * quando serve. Con -2.5 il collo da solo non basta (2.0 - 2.5 < 0) e serve il "non vedo
          * niente" (6.0) per accendere la scansione, che e' il solo caso in cui serve.
          */
        n_scanBias = -2.5;
        n_turnNothing = 1.0;
        n_moveNothing = 2.5;
        n_turnSated = -2.5;
    }

    private static void v3Parameters() {
        v4Parameters();
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
        dietAdapt = 0;    dietEffMin = 0.25; dietEffMax = 1.35;
        needPain = 0;     windowDecay = 0;   // il braccio vecchio: nessun dolore del bisogno
        v3Parameters();
        bodyEffectsOff();
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
        seedVersion = 4;
        maskActions = 1;
        allowAttack = 1;
        dietAdapt = 0.02; dietEffMin = 0.25; dietEffMax = 1.35;
        needPain = 0.5;   windowDecay = 0.1;
        v3Parameters();
        bodyEffectsOff();
    }

    /**
     * structured() con il CORPO CHE CONTA: la distanza coperta da un passo viene dall'andatura
     * misurata in fisica (Jolt), la massa pesa su salute e costo del movimento, la portata delle
     * estremita' decide da che distanza si mangia e si attacca. E' il profilo da confrontare con
     * structured() per vedere l'effetto della morfologia sulla sopravvivenza.
     */
    public static void embodied() {
        structured();
        bodyGaitSpeed = 1;
        bodyMassStats = 1;
        bodyReach = 1;
        bodyAttackPower = 1;
    }
}
