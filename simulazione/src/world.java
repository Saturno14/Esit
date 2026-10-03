package src;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class world {
    private static int Dimension = 50;
    private static cell[][][] Enviroment = new cell[Dimension][Dimension][Dimension];
    private static AtomicInteger ground = new AtomicInteger();   // livello medio del terreno (riferimento)
    private static int PrintGround = 0;
    private static int cycle = 0;

    // Terreno: per ogni colonna (x,z) l'indice y della prima cella d'aria; le celle d'aria piu'
    // in basso di waterLevel sono acqua (le depressioni si allagano).
    private static int[][] surface = new int[Dimension][Dimension];
    private static boolean[][] waterColumn = new boolean[Dimension][Dimension];
    private static int waterLevel = 0;
    private static boolean flatTerrain = false;
    private static volatile int terrainVersion = 0;

    private static final AtomicBoolean paused  = new AtomicBoolean(false);

    public static void ChengePrintGround(int value){
        PrintGround = value;
    }

    /** Con false ferma il ciclo di simulazione (vedi Simulation); true non fa nulla: si riparte con DoCycle(). */
    public static void SetRunning(boolean status){
        if(!status){ Simulation.stop(); }
    }

    public static boolean isPaused(){ return paused.get(); }
    public static boolean isRunning(){ return Simulation.isRunning(); }

    public static void Load(Path path){
        try {
            
            String content = Files.readString(Path.of(path+"/world.json"));
            String[] str = content.split("-");
            for(String s:str){
                String[] sub = s.split(":",2);
                for(int i =0;i<sub.length;i++){
                    if(sub[i].trim().isBlank()){continue;}
                    switch (sub[i].trim()) {
                        case "dimension":
                            Dimension=Integer.parseInt(sub[i+1].trim());
                            world_setup();
                            break;
                        case "cycle":
                            cycle=Integer.parseInt(sub[i+1].trim());
                            break;
                        case "ground":
                            ground.set(Integer.parseInt(sub[i+1].trim()));
                            break;
                        case "food":
                            String[] fd = sub[i+1].split(";");
                            for(String o: fd){
                                if(o.isBlank()){continue;}
                                String[] cord = o.split(",");
                                add(Integer.parseInt(cord[0].trim()),Integer.parseInt(cord[1].trim()),Integer.parseInt(cord[2].trim()),"M");
                            }
                            break;
                        default:
                            break;
                    }
                    
                }
            }
            

        } catch (Exception e) {System.out.println("Errore Load World "+e.getLocalizedMessage());}
        System.out.println("WorldLoaded");
    }

    public static int getCycle(){
        return cycle;
    }

    /** Passa al giorno successivo (chiamato dal ciclo centrale ogni Simulation.TICKS_PER_DAY tick). */
    public static void advanceCycle(){
        cycle++;
    }

    public static int getGround(){
        return ground.get();
    }

    /** Avvia il ciclo di simulazione centrale (mele, entita', cadaveri: vedi Simulation). */
    public static void DoCycle(){
        ChengePrintGround(ground.get());
        Simulation.start();
    }

    public static void PauseCycle(boolean status){
        paused.set(status);
    }

    // ------------------------------------------------------------------ terreno e acqua

    /** Terreno piatto (per confronti) invece del terreno irregolare; vale dal prossimo world_setup. */
    public static void setFlatTerrain(boolean flat){
        flatTerrain = flat;
    }

    public static boolean isFlatTerrain(){
        return flatTerrain;
    }

    /** Aumenta a ogni ricostruzione del terreno: il motore grafico lo usa per rifare la mesh dell'acqua. */
    public static int getTerrainVersion(){
        return terrainVersion;
    }

    /** Indice y della prima cella d'aria della colonna: e' dove stanno entita' e mele. */
    public static int surfaceY(int x, int z){
        return surface[x][z];
    }

    /** True se la colonna e' allagata (le entita' non ci possono entrare, ma ci possono bere accanto). */
    public static boolean isWaterColumn(int x, int z){
        return waterColumn[x][z];
    }

    /** Indice y della prima cella d'aria sopra l'acqua: la superficie dell'acqua sta a waterLevel - 0.5. */
    public static int getWaterLevel(){
        return waterLevel;
    }

    /** True se la cella contiene acqua. */
    public static boolean isWater(int x,int y, int z){
        return Enviroment[x][y][z].type[2] == 2;
    }

    /** Colonna casuale asciutta (per far nascere entita' e mele). Ritorna {x, z}. */
    public static int[] randomLandColumn(Random random){
        for(int attempt=0; attempt<200; attempt++){
            int x = random.nextInt(Dimension);
            int z = random.nextInt(Dimension);
            if(!waterColumn[x][z]){ return new int[]{x, z}; }
        }
        return new int[]{0, 0};
    }

    /** Numero di mele presenti nel mondo. */
    public static int countApples(){
        int count = 0;
        for(int x=0;x<Dimension;x++){
            for(int y=0;y<Dimension;y++){
                for(int z=0;z<Dimension;z++){
                    try {
                        if("M".equals(Enviroment[x][y][z].get_obgect())){ count++; }
                    } catch (Exception e) { }
                }
            }
        }
        return count;
    }

    public static void planetPrint(){
        try {
            int rows = Dimension;
            int columns = Dimension;
            String str;

            // Intestazione con indici di colonna
            str = "\t|\t";
            for (int j = 0; j < columns; j++) {
                str += j + "\t";
            }
            System.out.println(str);
            System.out.println("--------".repeat(columns + 1));

            // Righe con indice di riga a inizio
            for (int i = 0; i < rows; i++) {
                str = i + "\t|\t";
                for (int j = 0; j < columns; j++) {
                    int[] cord = {i,surface[i][j],j};
                    String str2 = "";
                    for(entity e : Entity_manager.snapshotAlive()){
                        int[] a = e.getPos();
                        if(Arrays.equals(a, cord) && e.isAlive()){
                            str2 = " E"+e.getId();
                        }
                    }
                    String cellSymbol = waterColumn[i][j] ? "~" : getSymbol(i, surface[i][j], j);
                    str +=  cellSymbol+str2+"\t";
                }
                System.out.println(str + "|");
            }
        } catch (Exception e) {
            System.out.println("Matrix is empty!! "+e.getLocalizedMessage());
        }
    }
    
    public static boolean world_setup(){
        if(!cell_setup()){return false;}
        if(!terrein_set()){return false;}

        return true;
    }


    public static int getDim(){
        return Dimension;
    }

    public static String getSymbol(int x,int y, int z){
        return Enviroment[x][y][z].get_obgect();
    }

    private static boolean cell_setup(){
        Enviroment = new cell[Dimension][Dimension][Dimension];
        for(int i=0;i<Dimension;i++){ //y
            for(int j=0;j<Dimension;j++){//x
                for(int f=0;f<Dimension;f++){//z
                    Enviroment[j][f][i] = new cell(j, i, f);
                }
            }
        }
        return true;
    }



    /**
     * Genera il terreno dal seed della simulazione: mappa delle altezze (irregolare, oppure
     * piatta se flatTerrain), terra sotto la superficie, aria sopra, e acqua nelle depressioni
     * (celle d'aria sotto waterLevel).
     */
    private static boolean terrein_set(){
        int base = (Dimension / 2) + 1;      // livello medio: prima cella d'aria di un terreno piatto
        waterLevel = base - 1;
        ground.set(base);

        surface = TerrainGenerator.generate(Dimension, base, Simulation.getSeed(), flatTerrain);
        waterColumn = new boolean[Dimension][Dimension];
        int flooded = 0;

        for(int x=0; x<Dimension; x++){
            for(int z=0; z<Dimension; z++){
                int h = surface[x][z];
                waterColumn[x][z] = !flatTerrain && h < waterLevel;
                if(waterColumn[x][z]){ flooded++; }
                for(int y=0; y<Dimension; y++){
                    if(y < h){
                        Enviroment[x][y][z].set_cellType("Terra");
                        Enviroment[x][y][z].set_obgect("X");
                    }else if(y < waterLevel){
                        Enviroment[x][y][z].set_cellType("Water");
                    }else{
                        Enviroment[x][y][z].set_cellType("Air");
                    }
                }
            }
        }
        terrainVersion++;
        System.out.println("Terreno: base="+base+" livello acqua="+waterLevel
                +" colonne allagate="+flooded+" (seed "+Simulation.getSeed()+(flatTerrain?", piatto":"")+")");
        return true;
        
    }

    public static int ground_search(){
        return ground.get();
    }    

    public static String check_cord_type(int x,int y, int z){
        return Enviroment[x][y][z].get_cellType();
    }

    public static int[] cord_Type(int x,int y, int z){
        return Enviroment[x][y][z].get_Type();
    }

    public static void add(int x,int y, int z, String simbol){
        if(simbol.equals("M")){
            Enviroment[x][y][z].set_obgect(simbol);
        }else if(simbol.equals("E")){
            Enviroment[x][y][z].set_Entity(true);
        }
        
    }

    public static void remove(int x, int y, int z, String simbol){
        if(Enviroment[x][y][z].get_obgect().equals(simbol)){
            Enviroment[x][y][z].set_obgect("");
        }
    }


    private static class cell{
        private int[] cordinate = new int[3]; //x,y,z
        private int[] type = {0,0,0}; //entità, oggetti, tipo di blocco (0 aria, 1 terra, 2 acqua)
        
        public cell(int x, int y, int z){
            cordinate[0] = x; //enviroment [x][][]
            cordinate[1] = z; //enviroment [][][x]
            cordinate[2] = y; //enviroment [][x][]
        }

        private  void set_cellType(String tipo){//aria/terra/acqua
            if(tipo.equals("Air")){type[2] = 0;}
            else if(tipo.equals("Terra")){type[2] = 1;}
            else if(tipo.equals("Water")){type[2] = 2;}
            
        }

        private void set_obgect(String simbol){
            if(simbol.equals("M")){type[1] = 1;}
            else if(simbol.isBlank()){type[1] = 0;}
        }

        private void set_Entity(boolean status){
            if(status){type[0]++;}
            else{type[0]--;}
        }

        public String get_obgect(){
            if(type[1] == 1){return "M";}
            if(type[1]==0){return "";}
            return  "";
        }

        private  String get_cellType(){
            if(type[2] == 1){return "Terra";}
            else if(type[2] == 2){return "Water";}
            else if(type[2] == 0){return "Air";}
            return  "";
        }

        public int[] get_Type(){
            return type;
        }

        
   }
}
