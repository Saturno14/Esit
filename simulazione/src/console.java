package src;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class console{

    private static int procedure = -1;

    /**
     * Elenco dei comandi disponibili, con una breve descrizione: e' quello che stampa "help".
     * Per aggiungere un nuovo comando in futuro basta chiamare registerCommand(...) nel blocco
     * statico qui sotto (stesso ordine con cui compaiono nello switch) e poi scrivere il case:
     * la lista di "help" si aggiorna da sola, senza bisogno di toccarla a mano.
     */
    private static final Map<String, String> COMMANDS = new LinkedHashMap<>();

    private static void registerCommand(String name, String description){
        COMMANDS.put(name, description);
    }

    static {
        registerCommand("help", "Elenca tutti i comandi disponibili con una breve descrizione");
        registerCommand("NewStart", "Avvia una nuova simulazione: nuovo mondo, entita' fondatrici, mele, nuova sessione genealogica");
        registerCommand("train [maxFood]", "NewStart di addestramento: riserva di cibo alta (default 2000); quando le entita' mangiano con regolarita' salva i pesi delle 10 che hanno mangiato piu' mele in trained/brains.json");
        registerCommand("maxfood <n>", "Imposta la riserva massima di cibo delle entita' (standard 200); vale per le entita' nate dopo");
        registerCommand("savebrains", "Salva subito i pesi delle 10 entita' vive che hanno mangiato piu' mele");
        registerCommand("trained on|off", "Le fondatrici dei prossimi NewStart partono dai pesi salvati (on) o dagli istinti (off)");
        registerCommand("start", "Riprende il ciclo di simulazione (se era in pausa)");
        registerCommand("stop", "Mette in pausa il ciclo di simulazione");
        registerCommand("speed <fattore>", "Imposta la velocita' della simulazione (es. speed 2 = doppia velocita')");
        registerCommand("seed <numero>", "Imposta il seed della simulazione: vale dal prossimo NewStart");
        registerCommand("terrain flat|piatto", "Terreno piatto invece che irregolare: vale dal prossimo NewStart (senza argomento: irregolare)");
        registerCommand("layer <n>", "Cambia il livello (altezza) mostrato da planetPrint");
        registerCommand("mela <x> <z>", "Aggiunge una mela in superficie alle coordinate indicate");
        registerCommand("setFood <id> <valore>", "Imposta il cibo dell'entita' con quell'id");
        registerCommand("Get <id>", "Mostra lo stato completo di un'entita' (salute, fame, sete, genoma, posizione...)");
        registerCommand("tree", "Riepilogo dell'albero genealogico della sessione corrente");
        registerCommand("tree <id>", "Antenati e numero di figli di una singola entita'");
        registerCommand("Save", "Salva mondo, entita' (con genoma e cervelli) e genealogia su disco");
        registerCommand("Load", "Elenca i salvataggi disponibili e chiede quale caricare");
    }

    public void setProcedure(int value){
        procedure = value;
    }

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
        StatsLog.open(Genealogy.getSession());

        // Fondatrici e mele iniziali scalate sulla dimensione della mappa (stessa densita' che
        // funzionava bene a 20x20: 10 fondatrici e mele fino al tetto), altrimenti su una mappa
        // grande le entita' nascono troppo isolate e senza abbastanza cibo a portata di vista.
        int startEntities = Math.max(10, Math.round(Simulation.maxPopulation() / 6f));
        boolean useTrained = TrainedBrains.useForFounders();
        System.out.println(useTrained
                ? "Fondatrici dai pesi addestrati ("+TrainedBrains.count()+" cervelli in trained/brains.json)"
                : "Fondatrici dagli istinti di base"
                    + (TrainedBrains.isArmed() ? " (simulazione di addestramento, riserva cibo "+entity.getMaxFood()+")" : ""));
        for(int i=0; i<startEntities; i++){
            int id = Entity_manager.newId();
            int[] col = world.randomLandColumn(Simulation.random());
            entity founder = new entity(id, world.getCycle(), col[0], world.surfaceY(col[0], col[1]), col[1]);
            founder.makeAdult();
            if(useTrained){
                founder.inheritBrain(TrainedBrains.founderBrain(i));
            }else{
                founder.seedInstincts();
            }
            Entity_manager.Entity_add(founder);
            Genealogy.recordFounder(founder);
            System.out.println("Entity: "+founder.getId()+" "+java.util.Arrays.toString(founder.getPos()));
        }
        int startApples = Simulation.maxApples();
        for(int i=0; i<startApples; i++){
            int[] col = world.randomLandColumn(Simulation.random());
            world.add(col[0], world.surfaceY(col[0], col[1]), col[1], "M");
        }
        System.out.println("Fondatrici: "+startEntities+", mele iniziali: "+startApples
                +" (mappa "+world.getDim()+"x"+world.getDim()+")");
        world.DoCycle();
    }

    private static String helpText(){
        StringBuilder sb = new StringBuilder("Comandi disponibili:\n");
        for(Map.Entry<String, String> e : COMMANDS.entrySet()){
            sb.append(" - ").append(e.getKey()).append(" : ").append(e.getValue()).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    public static String input(String input){
        String out = "";
        
        String cmd = input;
        
        if(procedure != -1){
            switch (procedure) {
                case 1:
                    StringBuilder str = new StringBuilder();
                    str.append("Load "+cmd);
                    cmd = str.toString();
                    break;
                default:
                    throw new AssertionError();
            }
        }


        if(!cmd.isEmpty()){
            String[] str = cmd.trim().split(" ");
            switch (str[0]) {
                case "help":
                    out = helpText();
                    break;
                case "layer":
                    if(Integer.parseInt(str[1])>=0 && Integer.parseInt(str[1])<world.getDim()){
                        world.ChengePrintGround(Integer.parseInt(str[1]));
                    }
                    out = ("Changed print layer to: "+Integer.parseInt(str[1]));
                    break;
                case "start":
                    world.PauseCycle(false);
                    break;
                case "stop":
                    world.PauseCycle(true);
                    break;
                case "speed":
                    try {
                        double scale = Double.parseDouble(str[1].trim().replace(',', '.'));
                        Simulation.setTimeScale(scale);
                        out = "Velocita' simulazione: x"+Simulation.getTimeScale();
                    } catch (Exception e) {
                        out = "Uso: speed <fattore>  (attuale: x"+Simulation.getTimeScale()+")";
                    }
                    break;
                case "seed":
                    try {
                        Simulation.setSeed(Long.parseLong(str[1].trim()));
                        out = "Seed impostato a "+Simulation.getSeed()+" (vale dal prossimo NewStart)";
                    } catch (Exception e) {
                        out = "Uso: seed <numero>  (attuale: "+Simulation.getSeed()+")";
                    }
                    break;
                case "tree":
                    if(str.length > 1){
                        try {
                            out = Genealogy.describe(Integer.parseInt(str[1].trim()));
                        } catch (Exception e) { out = "Uso: tree [id]"; }
                    }else{
                        out = Genealogy.summary();
                    }
                    break;
                case "terrain":
                    if(str.length > 1 && (str[1].equalsIgnoreCase("flat") || str[1].equalsIgnoreCase("piatto"))){
                        world.setFlatTerrain(true);
                        out = "Terreno piatto: attivo dal prossimo NewStart";
                    }else{
                        world.setFlatTerrain(false);
                        out = "Terreno irregolare: attivo dal prossimo NewStart";
                    }
                    break;
                case "mela":
                    try {
                        if(Integer.parseInt(str[1])>=0 && Integer.parseInt(str[1])<world.getDim()){
                            if(Integer.parseInt(str[2])>=0 && Integer.parseInt(str[2])<world.getDim()){
                                world.add(Integer.parseInt(str[1]), world.getGround(), Integer.parseInt(str[2]), "M");
                            }
                    }
                    } catch (Exception e) { System.out.println("Errore mela");}
                    break;
                case "setFood":
                    if(Integer.parseInt(str[2])>0){
                        Entity_manager.Entity_get(Integer.parseInt(str[1].trim())).setFood(Integer.parseInt(str[2].trim()));
                        out = ("Entity: "+Entity_manager.Entity_get(Integer.parseInt(str[1].trim())).getId()+" food set to: "+Integer.parseInt(str[2].trim()));
                    }
                    break;
                
                case "Get":
                    if(Integer.parseInt(str[1])>=0 && Integer.parseInt(str[1]) < Entity_manager.Entity_count()){
                        try {
                            entity temp = Entity_manager.Entity_get(Integer.parseInt(str[1]));
                            StringBuilder tempStr = new StringBuilder();
                            
                            tempStr.append("ID: "+temp.getId()+"\n");
                            tempStr.append("IsAlive: "+temp.isAlive()+"\n");
                            tempStr.append("Healt: "+temp.getHealt()+"\n");
                            tempStr.append("Food: "+temp.getFood()+"\n");
                            tempStr.append("Thirst: "+temp.getThirst()+"\n");
                            tempStr.append("Stamina: "+Math.round(temp.getStamina())+"/"+Math.round(temp.getStats().maxStamina)+"\n");
                            tempStr.append("Food_consumed: "+temp.getFoodConsumed()+"\n");
                            tempStr.append("Kills: "+temp.getKills()+"\n");
                            tempStr.append("NetRaward: "+temp.getNetreward()+"\n");
                            tempStr.append("Age: "+temp.getAge()+"\n");
                            tempStr.append("Generation: "+temp.getGenome().lineage().generation()+"\n");
                            tempStr.append("Diet: "+temp.getStats().diet+"\n");
                            tempStr.append("Sex: "+temp.getSex()+"\n");
                            tempStr.append("Pos: "+java.util.Arrays.toString(temp.getPos()));
                            out = tempStr.toString();
                            
                        } catch (Exception e) { out = ("Errore get Entity: "+ e.getMessage());}
                    }else{out = ("Errore in get input: "+str[1]);}
                    break;

                case "NewStart":
                    // Un NewStart normale torna alla riserva standard e non cattura pesi
                    entity.setMaxFood(entity.MAX_FOOD);
                    TrainedBrains.disarm();
                    startSimulation(true);
                    break;

                case "train":
                    try {
                        int cap = str.length > 1 ? Integer.parseInt(str[1].trim()) : 2000;
                        entity.setMaxFood(cap);
                        TrainedBrains.arm();
                        startSimulation(true);
                        out = "Addestramento: riserva di cibo "+cap+", cattura automatica dei pesi attiva";
                    } catch (Exception e) { out = "Uso: train [maxFood]"; }
                    break;

                case "maxfood":
                    try {
                        entity.setMaxFood(Integer.parseInt(str[1].trim()));
                        out = "Riserva massima di cibo: "+entity.getMaxFood()+" (standard "+entity.MAX_FOOD+")";
                    } catch (Exception e) { out = "Uso: maxfood <n>  (attuale: "+entity.getMaxFood()+")"; }
                    break;

                case "savebrains":
                    out = TrainedBrains.captureNow(Simulation.getTick());
                    break;

                case "trained":
                    if(str.length > 1 && str[1].equalsIgnoreCase("off")){
                        TrainedBrains.setEnabled(false);
                        out = "Fondatrici dagli istinti di base (dal prossimo NewStart)";
                    }else{
                        TrainedBrains.setEnabled(true);
                        out = TrainedBrains.exists()
                                ? "Fondatrici dai pesi salvati (dal prossimo NewStart)"
                                : "Attivo, ma trained/brains.json non esiste ancora: usa 'train' o 'savebrains'";
                    }
                    break;

                case "Save":
                    try {
                        Saving.Save();
                    } catch (Exception e) {System.out.println("Errore Save "+e.getMessage());}
                    out = "Saved";
                    break;

                case "Load":
                    int index = 0;
                    if(procedure != 1){
                        world.PauseCycle(true);
                        StringBuilder msg = new StringBuilder();
                        msg.append("Lista Salvataggi\n");
                        File root = new File("saving/");
                        File[] subdirs = root.listFiles(File::isDirectory);
                        for(File s : subdirs){
                            msg.append(index +"] "+s.getName()+"\n");
                            index++;
                        }

                        msg.append("Quale salvataggio vuoi caricare: \n");
                        procedure = 1;
                        utils.ClientSendMsg(msg.toString());
                    }else{
                        File[] subdirs = new File("saving/").listFiles(File::isDirectory);
                        int n = Integer.parseInt(str[1].trim());
                        if(n>=0 && n<subdirs.length){
                            if(Saving.Load(n)){out = "Salvataggio caricato";}
                            else{out= "errore caricamento salvataggio";}
                        }else{System.out.println("Numero salvataggio errato");}
                        procedure = -1;
                    }
                    break;
                    
                default:
                    out = ("Parametro: "+str[0]+" (comando sconosciuto: prova 'help')");
                    break;
            }
        }
        return out;
    }
    
}
