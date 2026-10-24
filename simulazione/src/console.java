package src;

public class console{

    /**
     * Avvio della simulazione: e' quello che fa il comando console "NewStart", esposto come
     * metodo statico cosi' puo' essere chiamato anche dal motore grafico all'avvio.
     * Ogni NewStart apre un nuovo albero genealogico e un nuovo log statistico.
     *
     * @param setupWorld true = ricrea il mondo (world_setup); false = il mondo e' gia' stato
     *                   creato da chi chiama (es. SimulationWorldBridge nel motore grafico)
     */
    public static void startSimulation(boolean setupWorld){
        int ground;

        Simulation.stop();
        Entity_manager.clearAll();
        Simulation.reset();
        Simulation.setSeed(Simulation.getSeed()); // riparte dal seed corrente: risultati riproducibili
        System.out.println("Seed simulazione: "+Simulation.getSeed());

        if(setupWorld){
            world.world_setup();
        }
        world.PauseCycle(false);
        ground = world.ground_search();

        Genealogy.newSession();
        History.reset();
        // anche l'ora del giorno riparte: un "ora notte" dato nella partita precedente non deve
        // restare attaccato al mondo nuovo (vedi DayNight.OFFSET)
        DayNight.OFFSET = 0;
        StatsLog.open(Genealogy.getSession());

        // Fondatrici scalate sulla dimensione della mappa (stessa densita' che funzionava bene a
        // 20x20: 10 fondatrici), altrimenti su una mappa grande le entita' nascono troppo isolate
        // e senza abbastanza cibo a portata di vista. Prima questo numero era una frazione del
        // tetto di popolazione: il tetto non c'e' piu' (vedi Simulation.limiteDiSicurezza) e le
        // fondatrici non c'entravano niente con un tetto, quindi adesso e' una densita' sua.
        int startEntities = Math.max(10, (int) Math.round(world.getDim() * world.getDim() * 0.025));
        System.out.println("Fondatrici dagli istinti di base");
        for(int i=0; i<startEntities; i++){
            int id = Entity_manager.newId();
            int[] col = world.randomLandColumn(Simulation.random());
            entity founder = new entity(id, world.getCycle(), col[0], world.surfaceY(col[0], col[1]), col[1]);
            founder.makeAdult();
            founder.seedInstincts();
            Entity_manager.Entity_add(founder);
            Genealogy.recordFounder(founder);
            System.out.println("Entity: "+founder.getId()+" "+java.util.Arrays.toString(founder.getPos()));
        }
        // Con gli alberi le mele iniziali le mettono le chiome (vedi Flora.generate): metterne
        // anche a caso in giro rifarebbe il mondo senza geografia che gli alberi servono a togliere.
        int startApples = Flora.enabled ? 0 : Simulation.maxApples();
        for(int i=0; i<startApples; i++){
            int[] col = world.randomLandColumn(Simulation.random());
            world.add(col[0], world.surfaceY(col[0], col[1]), col[1], "M");
        }
        System.out.println("Fondatrici: "+startEntities+", mele iniziali: "+startApples
                +" (mappa "+world.getDim()+"x"+world.getDim()+")");
        world.DoCycle();
    }

    /**
     * Esegue una riga di comando. L'analisi, l'aiuto e i messaggi d'errore stanno in
     * {@link Commands}: qui resta solo l'inoltro, cosi' la console di testo, la GUI e qualunque
     * altra cosa che mandi comandi passano dallo stesso registro e vedono gli stessi comandi.
     */
    public static String input(String input){
        return Commands.run(input);
    }
    
}
