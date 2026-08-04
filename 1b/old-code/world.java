package src;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class world {
    /*mi serve una matrice tridimensionale di oggetti celle. perchè c'è la sovrapposizione degli elementi come entità 
    e oggetti. ogni cella sarà definita dalle sue cordinate x,y,z 
    il probblema che gia una matrice 3d non posso visuallizarla nel terminale
    */

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

    //PathFinder section
    private static int PathF_calcTotCost(int ax, int ay, int bx, int by){
        int movex = Math.abs(ax-bx);
        int movey = Math.abs(ay-by);
        int Diagonalpass = Math.min(movex,movey);
        int Rightpass = Math.abs(movex-movey);
        int tot = (Diagonalpass*14)+(Rightpass*10);
        return tot;
    }

    public static ArrayList[] PathSearch(int ax, int ay,String Symbol){
        int[] A = new int[2];
        int[] B = new int[2];
        ArrayList[] XList = new ArrayList[2];
        XList[0] = new ArrayList<Integer>();
        XList[1] = new ArrayList<Integer>();
        A[0]= ax;
        A[1]= ay;
        B[0]=-1;
        B[1]=-1;
        for(int i=0; i<Dimension; i++){
            for(int j=0;j<Dimension;j++){
                if(i==ax && j == ay){continue;}
                if(!Enviroment[i][ground.get()][j].get_obgect().isBlank()){
                    if(Enviroment[i][ground.get()][j].get_obgect().equals(Symbol)){B[0] = i; B[1]=j;}
                    else{XList[0].add(i);XList[1].add(j);}
                }
            }
        }
        String[][] mat = new String[Dimension][Dimension];
        System.out.println("Trovata mela: "+B[0]+" "+B[1]);
        mat[A[0]][A[1]]="A";
        mat[B[0]][B[1]]="B";
        int x = 0;
        int y = 0;
        ArrayList[] map = new ArrayList[3];
        map[0]= new ArrayList<Integer>();
        map[1]= new ArrayList<Integer>();
        map[2]= new ArrayList<Integer>();

        ArrayList[] path = new ArrayList[2];
        path[0]= new ArrayList<Integer>();
        path[1]= new ArrayList<Integer>();
        boolean flag = false;
        //devi mettere un sistema di ordinamento in base al Total cost degli oggetti
        boolean flag3 = false;
        if(B[0]==-1 && B[1]==0){flag3 = true;}
        if(flag3){flag = true;}
        while(!flag){
            boolean flag1 = false;
            if(!flag){
                if(map[0].isEmpty()&&path[0].isEmpty()){x=A[0];y=A[1];}
                else if(map[0].isEmpty()){System.out.println("Nessuna soluzione possibile");break;}
                else{
                    if(map[0].size()>1){
                        for(int i=0; i<map[0].size()-1;i++){
                            for(int j=i+1;j<map[0].size();j++){
                                if((int)map[0].get(i) > (int)map[0].get(j)){
                                    int temp = (int)map[0].get(j);
                                    map[0].set(j, map[0].get(i));
                                    map[0].set(i, temp);
                                    temp = (int)map[1].get(j);
                                    map[1].set(j, map[1].get(i));
                                    map[1].set(i, temp);
                                    temp = (int)map[2].get(j);
                                    map[2].set(j, map[2].get(i));
                                    map[2].set(i, temp);  
                                }
                            }
                        }
                    }

                    x = (int)map[1].get(0);
                    y = (int)map[2].get(0);
                    if(mat[x][y].equals("#")){mat[x][y]="@";}
                    path[0].add(x);
                    path[1].add(y);
                    flag1 = true;
                }
                if(x == B[0] && y == B[1]){
                    flag = true; 
                    System.out.println(path[0]);
                    System.out.println(path[1]);
                }
            }
            if(!flag){
                int xstart = x;
                int xend = x;
                int ystart = y;
                int yend = y;

                if(x>0){xstart = x-1;}
                if(x<Dimension-1){xend= x+1;}
                if(y>0){ystart= y-1;}
                if(y<Dimension-1){yend= y+1;}
                
                for(int i=xstart;i<=xend;i++){
                    for(int j=ystart; j<=yend;j++){
                        if(mat[i][j]!=null){
                            if(i==x && j == y){continue;}
                            if(mat[i][j].equals("X")){continue;}
                            if(mat[i][j].equals("@")){continue;}
                        }
                        boolean flag2 = false;
                        System.out.println("x: "+i+" y: "+j);
                        int tot = PathF_calcTotCost(i, j, B[0], B[1]);
                            for(int z = 0;z<map[0].size();z++){
                                if(i == (int)map[1].get(z) && j == (int)map[2].get(z)){
                                    map[0].set(z, tot);
                                    flag2 = true;
                                }
                            }
                            if(!flag2){
                                map[0].add(tot);
                                map[1].add(i);
                                map[2].add(j);
                            }
                        if(mat[i][j] == null){ mat[i][j]="#";}
                    
                    }
                }
                
                if(flag1){map[0].remove(0);map[1].remove(0);map[2].remove(0);}
            }
        }if(flag3){
            System.out.println("Nessuna oggetto in quella cella pathserach");
            return null;
        }
        return path;
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

        
   }
}