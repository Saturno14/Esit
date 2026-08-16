package src;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class world {
    private static AtomicBoolean SimulationFlag = new AtomicBoolean();
    private static int Dimension = 20;
    private static cell[][][] Enviroment = new cell[Dimension][Dimension][Dimension];
    private static AtomicInteger ground = new AtomicInteger();
    private static int PrintGround = 0;
    private static int cycle = 0;

    private static volatile Thread cycleThread;          // riferimento persistente
    private static final AtomicBoolean running = new AtomicBoolean(false); // "deve girare"
    private static final AtomicBoolean paused  = new AtomicBoolean(false);

    public static void ChengePrintGround(int value){
        PrintGround = value;
    }

    public static boolean isPaused(){ return paused.get(); }
    public static boolean isRunning(){ return running.get(); }

    public void Load(){
        try {
            String content = Files.readString(Path.of("saving/world.json"));
            

        } catch (Exception e) {
        }
    }

    public static int getCycle(){
        return cycle;
    }

    public static int getGround(){
        return ground.get();
    }

    public static void DoCycle(){
        AtomicBoolean CycleFlag = new AtomicBoolean();
        ChengePrintGround(ground_search());      
        if(!running.compareAndSet(false, true)){
            System.out.println("Cycle già attivo, ignoro");
            return;
        }
        cycleThread = new Thread(() -> {
            while(running.get()){
                if(paused.get()){
                    try { Thread.sleep(100); } catch(InterruptedException ignored){}
                    continue; 
                }
                int ore = 0;
                System.out.println("Cycle start");
                try {
                    while(world.GetSimulationFlag()){
                        Thread.sleep(1000);//5 secondo
                        System.out.println("Dentro doCycle flag= "+CycleFlag.get()+"threadN: "+Thread.currentThread());
                        System.out.println("Cycle: "+cycle+" - ore: "+ore);
                        System.out.println("print layer: "+ground.get());
                        System.out.println("Total Entity: "+Entity_manager.get_EntityN());
                        System.out.println("Entity count: "+Entity_manager.Entity_count());
                        try {
                            for(int i = 0; i<Entity_manager.Entity_count()%2; i++){
                                add((int)(Math.random()*20), ground.get(), (int)(Math.random()*20), "M");
                            }
                            world.planetPrint();
                        } catch (Exception e) {System.out.println("Errore try cycle: "+e.getMessage());}
                        ore++;
                        if(ore == 24){
                            ore = 0;
                            cycle++;
                            System.out.println("Nuovo cyclo");
                        }
                    }
                    System.out.println("TotCycle stopped");
                    
                } catch (Exception e) {
                    System.out.println("Errore DoCycle thread: "+e.getMessage());
            }
            }
            System.out.println("Cycle terminato");
        });
        cycleThread.start();
        
    }

    public static void PauseCycle(boolean status){
        paused.set(status);
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
                    int[] cord = {i,PrintGround,j};
                    String str2 = "";
                    for(int z=0;z<Entity_manager.Entity_count();z++){
                        int[] a = Entity_manager.Entity_get(z).getPos();
                        if(Arrays.equals(a, cord) && Entity_manager.Entity_get(z).isAlive()){
                            str2 = " E"+Entity_manager.Entity_get(z).getId();
                        }
                    }
                    str +=  getSymbol(i, PrintGround, j)+str2+"\t";
                }
                System.out.println(str + "|");
            }
        } catch (Exception e) {
            System.out.println("Matrix is empty!! "+e.getLocalizedMessage());
        }
    }

    public static void ChangeSimulationFlag(boolean status){
        SimulationFlag.set(status);
    }

    public static boolean GetSimulationFlag(){
        return SimulationFlag.get();
    }
    
    public static boolean world_setup(){
        if(!cell_setup()){return false;}
        if(!terrein_set()){return false;}
        ground.set(ground_search());

        return true;
    }


    public static int getDim(){
        return Dimension;
    }

    public static String getSymbol(int x,int y, int z){
        return Enviroment[x][y][z].get_obgect();
    }

    private static boolean cell_setup(){
        for(int i=0;i<Dimension;i++){ //y
            for(int j=0;j<Dimension;j++){//x
                for(int f=0;f<Dimension;f++){//z
                    Enviroment[j][f][i] = new cell(j, i, f);
                }
            }
        }
        return true;
    }



    private static boolean terrein_set(){
        //imposto che la metà bassa dellìaltezza è terra, e la metà alta aria, per ora non c'è acqua
        int low_site = 0;
        if(Dimension%2==0){low_site = Dimension/2;}else{low_site = (Dimension-1)/2;}
        System.out.println("Low_site= "+low_site);

        for(int i=0; i<Dimension;i++){
            for(int j=0;j<Dimension;j++){
                for(int z=0;z<Dimension;z++){
                    try {
                        if(i<=low_site){
                            Enviroment[j][i][z].set_cellType("Terra");
                            Enviroment[j][i][z].set_obgect("X");
                            System.out.println("set up cord: "+j+"-"+i+"-"+z+" == "+ Enviroment[j][i][z].get_cellType());}
                        else{Enviroment[j][i][z].set_cellType("Air");
                            System.out.println("set up cord: "+j+" +-"+i+"-"+z+" == "+ Enviroment[j][i][z].get_cellType());}
                    } catch (Exception e) {
                    }
                }
            }
        }
        return true;
        
    }

    public static int ground_search(){
        int ground=0;
        for(int i= 0;i<Dimension;i++){
            System.out.println("check cord: 0-"+i+"-0 == "+check_cord_type(0,i,0));
            if(check_cord_type(0,i,0).equals("Air")){
                break;
            }
            ground++;
        }
        System.out.println("ground trove: "+ground);
        return ground;
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
        private int[] type = {0,0,0}; //entità, oggetti, tipo di blocco
        
        public cell(int x, int y, int z){
            cordinate[0] = x; //enviroment [x][][]
            cordinate[1] = z; //enviroment [][][x]
            cordinate[2] = y; //enviroment [][x][]
        }

        private  void set_cellType(String tipo){//aria/ground
            if(tipo.equals("Air")){type[2] = 0;}
            else if(tipo.equals("Terra")){type[2] = 1;}
            
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
            else if(type[2] == 0){return "Air";}
            return  "";
        }

        public int[] get_Type(){
            return type;
        }

        
   }
}