package src;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import src.brain.NeuralNetwork;
import src.brain.Reproduction;


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
    private int food_count = 0;
    private double Netreward = 0;
    private AtomicInteger[] position = new AtomicInteger[3];
    private int[] posi = new int[3];
    public String process = "";
    private ArrayList[] NecessityList = new ArrayList[3];
    private ArrayList[] MoveList = new ArrayList[2];
    public AtomicBoolean life = new AtomicBoolean();
    private AtomicInteger tick = new AtomicInteger();
    private String quest = "";
    public NeuralNetwork Brain;


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
        Brain = new NeuralNetwork(80,32,16,6);
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

    public double[] get_NetInput(){
        int xmin , ymin, xmax ,ymax;
                if(position[0].get()-2 >=0){
                    xmin = -2;
                }else{
                    if(position[0].get()-1>=0){xmin = -1;}
                    else{xmin = 0;}
                }

                if(position[1].get()-2 >=0){
                    ymin = -2;
                }else{
                    if(position[1].get()-1>=0){ymin = -1;}
                    else{ymin = 0;}
                }

                if(position[0].get()+2 <= world.getDim()-1){
                    xmax = +2;
                }else{
                    if(position[0].get()-1 <=world.getDim()-1){xmax = +1;}
                    else{xmax = 0;}
                }

                if(position[1].get()+2 <= world.getDim()-1){
                    ymax = +2;
                }else{
                    if(position[1].get()-1 <=world.getDim()-1){ymax = +1;}
                    else{ymax = 0;}
                }

                double[] input = new double[80]; //75
                int counter = 0;
                for(int i = position[0].get()+xmin; i<=position[0].get()+xmax; i++){
                    for(int j = position[1].get()+ymin; j<=position[1].get()+ymax; j++){
                        int[] info = world.cord_Type(i, position[1].get(), j);
                            input[counter] = info[0];
                            input[counter++] = info[1];
                            input[counter++] = info[2];
                    }
                }
                //0-24
                input[counter++]= healt/100.0;
                input[counter++]= food/100.0;
                input[counter++]= stamina/100.0;
                input[counter++]= position[0].get()/(double)world.getDim();
                input[counter++]= position[1].get()/(double)world.getDim();
                return input;
    }

    private void reproduce(){

        int id = Entity_manager.Entity_count();

        entity child = new entity(
                id,
                tick.get(),
                position[0].get(),
                position[1].get(),
                position[2].get()
        );


        // eredita cervello del padre con mutazioni
        child.Brain = Reproduction.createChild(this.Brain);


        Entity_manager.Entity_add(child);


        new Thread(() -> {
            child.GoLife();
        }).start();


        System.out.println(
            "Nascita ID "+id+
            " da padre "+EntityID
        );
    }

    public void GoLife(){
        life.set(true);

        System.out.println("appena vivo id: "+EntityID+" - Thr: "+process+" pos: "+position[0].get()+" "+position[1].get());
        
        while(life.get()){
            try {
                Thread.sleep(2000);
                tick.addAndGet(1);
                food -= 5;
                Netreward += 0.5;

                if(food <= 0){
                    healt -= 5;
                }
                if(food >= 85){if(healt < 100){healt ++;}}
                if(healt <= 0){life.set(false);}

                if(food_count >= 3){
                    reproduce();
                    food_count = 0;
                }
                

                double[] output = Brain.predict(get_NetInput());

                int best = 0;
                for (int i = 1; i < output.length; i++) {
                    if (output[i] > output[best]) {
                        best = i;
                    }
                }

                switch (best) {
                    case 1:
                        if(position[0].get()+1 <= 20){
                            MoveList[0].add(position[0].get()+1);
                            MoveList[1].add(position[2].get());
                            Netreward += 0.5;
                        }else{Netreward -= 10;}
                        break;
                    case 2:
                        if(position[0].get()-1 >= 0){
                            MoveList[0].add(position[0].get()-1);
                            MoveList[1].add(position[2].get());
                            Netreward += 0.5;
                        }else{Netreward -= 10;}
                        break;
                    case 3:
                        if(position[2].get()+1 <= 20){
                            MoveList[0].add(position[0].get());
                            MoveList[1].add(position[2].get()+1);
                            Netreward += 0.5;
                        }else{Netreward -= 10;}
                        break;
                    case 4:
                        if(position[2].get()-1 >= 0){
                            MoveList[0].add(position[0].get());
                            MoveList[1].add(position[2].get()-1);
                            Netreward += 0.5;
                        }else{Netreward -= 10;}
                        break;
                    case 5:
                        MoveList[0].add(-1);
                        MoveList[1].add(-1);
                        break;
                    case 6:
                        continue;
                    default:
                        throw new AssertionError();
                }

                if(!MoveList[0].isEmpty()){
                    if(MoveList[0].get(0).equals(-1)){
                        if(world.getSymbol(position[0].get(),position[1].get(),position[2].get()).equals("M")){
                            System.out.println("Take mela");
                            takeObject();
                            MoveList[0].remove(0);
                            MoveList[1].remove(0);
                            
                        }
                    }else{
                        System.out.println("muove in: "+MoveList[0].get(0)+" "+MoveList[1].get(0));
                        int temp = (int)MoveList[0].get(0);
                        position[0].set(temp);
                        temp = (int)MoveList[1].get(0);
                        position[2].set(temp);
                        MoveList[0].remove(0);
                        MoveList[1].remove(0);
                    }
                   
                }


            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            //if(tick.) // qua devi trovare un modo per sincronizzare i tick provenienti dal main
            //water --; salta la sete per ora
        }
        Entity_manager.Entity_remuve_ID(EntityID);
        Netreward -= 100.0;
        
    }


    public void setFood(int value){
        this.food = value;
    }

    private boolean takeObject(){
        boolean flag = false;
        int old_food = food;
        String str = world.getSymbol(position[0].get(),position[1].get(),position[2].get());
        System.out.println("take object+ "+str);
        System.out.println("cord: "+position[0].get()+" "+position[1].get()+" "+position[2].get());

        switch (str){
            case "M":
                world.remove(position[0].get(),position[1].get(),position[2].get(),str);
                System.out.println("dentro take mela");
                food += 50;
                if(food>100){food=100;}
                food_count ++;
                if(old_food != food){Netreward += 1.0;}
                break;
            default:
                System.err.println("Errore take Object Entity ID: "+EntityID+" str: "+str);
                Netreward -= 2.0;
                break;
        }

        return flag;
    }

    public void DayPass(int tick){
        if(tick%(100*born)==0){
            born++;
        }
    }
    
}