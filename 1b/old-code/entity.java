package src;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * entity
 */
public class entity {
    private String entity_type = "";
    private int EntityID;
    private int healt;
    private int water;
    private int food;
    private int stamina;
    private int born;
    private int age;
    private int sex;
    private AtomicInteger[] position = new AtomicInteger[3];
    private int[] posi = new int[3];
    public String process = "";
    private ArrayList[] NecessityList = new ArrayList[3];
    private ArrayList[] MoveList = new ArrayList[2];
    public static AtomicBoolean life = new AtomicBoolean();
    private AtomicInteger tick = new AtomicInteger();
    private String quest = "";


    public entity(int id, int cycle, int x, int y, int z){
        this.entity_type= "human";
        this.EntityID = id;
        this.healt = 100;
        this.water = 100;
        this.food = 100;
        this.stamina = 100;
        this.born = cycle;
        this.age = 0;
        this.sex = (int)(Math.random()*(2-1))+1; //2 femmina, 1 maschio
        tick.set(0);
        NecessityList[0] = new ArrayList<String>();
        NecessityList[1] = new ArrayList<Integer>();
        NecessityList[2] = new ArrayList<String>();
        MoveList[0] = new ArrayList<Integer>();
        MoveList[1] = new ArrayList<Integer>();
        position[0] = new AtomicInteger(x);
        position[1] = new AtomicInteger(y);
        position[2] = new AtomicInteger(z);
        
    }

    public int[] getPos(){
        int[] pos = new int[3];
        pos[0] = position[0].get();
        pos[1] = position[1].get();
        pos[2] = position[2].get();
        return pos;
    }

    public int getId(){
        return EntityID;
    }

    

    public void setProcessId(String value){
        this.process = value;
    }

    

    public void GoLife(){
        life.set(true);

        System.out.println("appena vivo id: "+EntityID+" - Thr: "+process+" pos: "+position[0].get()+" "+position[1].get());
        while(life.get()){
            try {
                Thread.sleep(2000);
                tick.addAndGet(1);
                NecessityFound();
                NecessityListCheck();
                if(!MoveList[0].isEmpty()){
                    if(MoveList[0].get(0).equals(-1)){
                        System.out.println("pre if take mela");
                        if(world.getSymbol(position[0].get(),position[1].get(),position[2].get()).equals("M")){
                            System.out.println("Take mela");
                            takeObject();
                            MoveList[0].remove(0);
                            MoveList[1].remove(0);
                            NecessityList[0].remove(0);
                            NecessityList[1].remove(0);
                            NecessityList[2].remove(0);
                            
                        }
                    }else{
                        System.out.println("muove in: "+MoveList[0].get(0)+" "+MoveList[1].get(0));
                        int temp = (int)MoveList[0].get(0);
                        position[0].set(temp);
                        temp = (int)MoveList[1].get(0);
                        position[2].set(temp);
                        MoveList[0].remove(0);
                        MoveList[1].remove(0);
                        entity_view();
                    }
                   
                }

            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            //if(tick.) // qua devi trovare un modo per sincronizzare i tick provenienti dal main
            //water --; salta la sete per ora
        }
        
    }

    private void entity_view(){
        
    }

    public void setFood(int value){
        this.food = value;
    }

    private boolean takeObject(){
        boolean flag = false;
        String str = world.getSymbol(position[0].get(),position[1].get(),position[2].get());
        System.out.println("take object+ "+str);
        System.out.println("cord: "+position[0].get()+" "+position[1].get()+" "+position[2].get());

        switch (str){
            case "M":
                world.remove(position[0].get(),position[1].get(),position[2].get(),str);
                System.out.println("dentro take mela");
                food += 50;
                if(food>100){food=100;}
                break;
            default:
                System.err.println("Errore take Object Entity ID: "+EntityID+" str: "+str);
                break;
        }

        return flag;
    }

    private boolean NecessityFound(){
        //priorità in una scala da 1 a 10; 1-top, 10-low
        boolean flag = false;
        if(food < 100-(food/4)){
            if(NecessityList[0].indexOf("food") == -1){
                NecessityList[0].add("food");
                NecessityList[1].add(6);
                NecessityList[2].add("Wait");
            }
            if(food < 100-(food/2)){
                NecessityList[1].set(NecessityList[0].indexOf("food"), 4);
            }
            if(food < 100-(100-15)){
                NecessityList[1].set(NecessityList[0].indexOf("food"), 1);
            }
        }
        else{
            if(NecessityList[0].indexOf("food") != -1){
                NecessityList[0].remove(0);
                NecessityList[1].remove(0);
                NecessityList[2].remove(0);
            }
        }



        return flag;
    }

    private void NecessityListCheck(){
        if(!NecessityList[0].isEmpty()){
            String temp0 = "";
            int temp1 = 0;
            String temp2 = "";

            for(int i=0;i<NecessityList[0].size()-1;i++){
                for(int j=1;j<NecessityList[0].size();j++){
                    if((int)NecessityList[1].get(i)>(int)NecessityList[1].get(j)){
                        temp0 = NecessityList[0].get(i).toString();
                        temp1 = (int)NecessityList[1].get(i);
                        temp2 = NecessityList[2].get(i).toString();
                        NecessityList[0].set(i, NecessityList[0].get(j));
                        NecessityList[1].set(i, NecessityList[1].get(j));
                        NecessityList[2].set(i, NecessityList[2].get(j));
                        NecessityList[0].set(j, temp0);
                        NecessityList[1].set(j, temp1);
                        NecessityList[2].set(j, temp2);
                    }
                }
            }

            if(NecessityList[0].get(0).equals("food") && NecessityList[2].get(0).equals("Wait")){
                NecessityList[2].set(0, "Run");
                ArrayList[] path = new ArrayList[2];
                path = world.PathSearch(position[0].get(), position[2].get(), "M");
                if(path != null){
                    for(int i=0; i<path[0].size();i++){
                        MoveList[0].add(path[0].get(i));
                        MoveList[1].add(path[1].get(i));
                    }
                }
                MoveList[0].add(-1); //-1 = take
                MoveList[1].add(-1);

                System.out.println("Fine Food necessity: ");
                System.out.println(MoveList[0]);
                System.out.println(MoveList[1]);
            }
        }
    }

    public void DayPass(int tick){
        if(tick%(100*born)==0){
            born++;
        }
    }

    
    //ogni entità secondo me dovrebbe essere un thread a parte, autonomo. 
    // quindi serve un contatore all'interno del main che mi sincronizzi tutte le entità 
    //in base alla sincronizzazione vengono scalate le stats, ed in base alle stats ci sono le necessità
    //le necessità però vengono eseguite nella mappa quindi per ogni oggetto e movimento 
    // deve essere interrogata la mappa. 
    
}