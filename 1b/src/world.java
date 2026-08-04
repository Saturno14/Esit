package src;

import java.util.concurrent.atomic.AtomicInteger;

public class world {
    private static int Dimension = 20;
    private static cell[][][] Enviroment = new cell[Dimension][Dimension][Dimension];
    private static AtomicInteger ground = new AtomicInteger();

    
    public boolean world_setup(){
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

    private boolean cell_setup(){
        for(int i=0;i<Dimension;i++){ //y
            for(int j=0;j<Dimension;j++){//x
                for(int f=0;f<Dimension;f++){//z
                    Enviroment[j][f][i] = new cell(j, i, f);
                }
            }
        }
        return true;
    }



    private boolean terrein_set(){
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
            cordinate[1] = z;//enviroment [][][x]
            cordinate[2] = y;//enviroment [][x][]
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