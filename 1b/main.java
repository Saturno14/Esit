import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import src.*;
public class main {

    private static world planet = new world();
    
    private static AtomicInteger ground = new AtomicInteger();    

    public static void main(String[] args) {

        int start_entity = 10;
        int Entity_count = 0;
        world.ChangeSimulationFlag(true);
        planet.world_setup();
        ground.set(planet.ground_search());
        
        

        for(int i=0; i<=start_entity;i++){
            entity tempEntity = new entity(Entity_count,planet.getCycle(),(int)(Math.random()*(planet.getDim()-1))+1,ground.get(),(int)(Math.random()*(planet.getDim()-1))+1);
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
        for(int i=0;i<20; i++){
            planet.add((int)(Math.random()*20), ground.get(), (int)(Math.random()*20), "M");
        }

        ProcessBuilder Console = new ProcessBuilder("cmd", "/c", "start", "cmd", "/k", "java console.java");
        try {
            Console.start();
        } catch (Exception e) { System.out.println("Errore console main: "+e.getMessage());}
        
        planet.DoCycle();



        
    }
}