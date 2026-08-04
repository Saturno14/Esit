import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import src.*;
public class main {

    private static world planet = new world();
    
    private static int cycle = 0;
    private static AtomicBoolean CycleFlag = new AtomicBoolean();
    private static AtomicBoolean TotCycle = new AtomicBoolean();
    private static AtomicInteger ground = new AtomicInteger();


    private static void planetPrint(int ground){
        try {
            int rows = planet.getDim();
            int columns = planet.getDim();
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
                    int[] cord = {i,ground,j};
                    String str2 = "";
                    for(int z=0;z<Entity_manager.Entity_count();z++){
                        int[] a = Entity_manager.Entity_get(z).getPos();
                        if(Arrays.equals(a, cord)){
                            str2 = " E"+Entity_manager.Entity_get(z).getId();}
                    }
                    str +=  planet.getSymbol(i, ground, j)+str2+"\t";
                }
                System.out.println(str + "|");
            }
        } catch (Exception e) {
            System.out.println("Matrix is empty!! "+e.getLocalizedMessage());
        }
    }

    private static void DoCycle(){
        TotCycle.set(true);
        Thread Cycle = new Thread(()->{
            int ore = 0;
            System.out.println("Cycle start");
            try {
                while(TotCycle.get()){
                    Thread.sleep(1000);//5 secondo
                    while(CycleFlag.get()){
                        Thread.sleep(2000);
                        System.out.println("Dentro doCycle flag= "+CycleFlag.get()+"threadN: "+Thread.currentThread());
                        System.out.println("Cycle: "+cycle+" - ore: "+ore);
                        System.out.println("print layer: "+ground.get());
                        System.out.println("Entity count: "+Entity_manager.Entity_count());
                        try {
                            //qua devi aggiungere tutta la parte di come si svolge il ciclo giornaliero
                            planetPrint(ground.get());
                            // for(int i = 0; i<EntityList.size()-1;i++){
                            //     int b[] = EntityList.get(i).getPos();
                            //     System.out.print("Pos: "+b[0]);
                            //     System.out.println(" - "+b[1]);
                            // }
                            
                        } catch (Exception e) {System.out.println("Errore try cycle: "+e.getMessage());}
                        ore++;
                        if(ore == 24){
                            ore = 0;
                            cycle++;
                            System.out.println("Nuovo cyclo");
                        }
                    }
                    System.out.println("Cycle stopped");
                }
                System.out.println("TotCycle stopped");
                
            } catch (Exception e) {
                System.out.println("Errore DoCycle thread: "+e.getMessage());
            }
        });
        CycleFlag.set(true);
        System.out.println("Avvio DoCycle");
        Cycle.start();
    }

    private static void console(){
        new Thread(()->{
            while(true){
                String cmd = "";
                Scanner sc = new Scanner(System.in);
                cmd= sc.nextLine();
                if(!cmd.isEmpty()){
                    String[] str = cmd.split(" ");
                    switch (str[0]) {
                        case "layer":
                            if(Integer.parseInt(str[1])>=0 && Integer.parseInt(str[1])<planet.getDim()){
                                try {
                                    CycleFlag.set(false);
                                    Thread.sleep(5000);
                                } catch (InterruptedException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                                ground.set(Integer.parseInt(str[1]));
                                CycleFlag.set(true);
                            }
                            break;
                        case "start":
                            CycleFlag.set(true);
                            break;
                        case "stop":
                            CycleFlag.set(false);
                            break;
                        case "TotStop":
                            CycleFlag.set(false);
                            TotCycle.set(false);
                            break;
                        case "TotStart":
                            TotCycle.set(true);
                            DoCycle();
                            break;
                        case "mela":
                            try {
                                if(Integer.parseInt(str[1])>=0 && Integer.parseInt(str[1])<=planet.getDim()){
                                    if(Integer.parseInt(str[2])>=0 && Integer.parseInt(str[2])<=planet.getDim()){
                                        planet.add(Integer.parseInt(str[1]), ground.get(), Integer.parseInt(str[2]), "M");
                                    }
                            }
                            } catch (Exception e) { System.out.println("Errore mela");}
                            break;
                        case "setFood":
                            if(Integer.parseInt(str[2])>0){
                                Entity_manager.Entity_get(Integer.parseInt(str[1].trim())).setFood(Integer.parseInt(str[2].trim()));
                                System.out.println("Entity: "+Entity_manager.Entity_get(Integer.parseInt(str[1].trim())));
                            }
                            break;
                        
                            case "print":
                                planetPrint(ground.get());
                                break;
                            
                        default:
                            System.out.println("Parametro: "+str[0]);
                            break;
                    }
                }
            }
        }).start();
        System.out.println("Fuori doCycle");
    }


    public static void main(String[] args) {

        int start_entity = 10;
        int Entity_count = 0;
        boolean flag = true;
          
        planet.world_setup();
        ground.set(planet.ground_search());
        
        

        for(int i=0; i<=start_entity;i++){
            entity tempEntity = new entity(Entity_count,cycle,(int)(Math.random()*(planet.getDim()-1))+1,ground.get(),(int)(Math.random()*(planet.getDim()-1))+1);
            Entity_manager.Entity_add(tempEntity);
            int[] temppos = Entity_manager.Entity_get(Entity_manager.Entity_count()-1).getPos();
            planet.add(temppos[0],temppos[1],temppos[2],"E");
            System.out.println("Entity: "+Entity_manager.Entity_get(i).getId()+" "+Arrays.toString(Entity_manager.Entity_get(i).getPos()));
            new Thread(()->{
                String temp = "";
                temp = Thread.currentThread().getName();
                tempEntity.setProcessId(temp);
                tempEntity.GoLife();
            }).start();
            Entity_count++;
        }
        Entity_count = Entity_manager.Entity_count();
        planet.add(5, ground.get(), 6, "M");

        console();
        DoCycle();



        
    }
}