import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import src.*;
public class main {

    private static world planet = new world();
    
    private static AtomicInteger ground = new AtomicInteger(); 
    
    public static void client(){
        try {
            Socket socket = new Socket("localhost",5443);
            System.out.println("collegato al server");

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            new Thread(()->{
                while(true){
                    try {
                        String output = "";
                        String str = "";
                        str = in.readLine();
                        System.out.println("report: "+str);
                        output = console.input(str);
                        out.println("Comand output: "+output);

                    } catch (Exception e) {System.out.println("Errore in server in");}
                }
            }).start();
        } catch (Exception e) {System.out.println("Errore in clint: "+e.getMessage());}


        
    }

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

        ProcessBuilder Console = new ProcessBuilder("cmd", "/c", "start", "cmd", "/k", "java ConsoleInput.java");
        try {

            Console.start();
            client();
        } catch (Exception e) { System.out.println("Errore console main: "+e.getMessage());}
        
        planet.DoCycle();




        
    }
}