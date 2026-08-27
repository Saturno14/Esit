import java.util.concurrent.atomic.AtomicInteger;
import src.*;
public class main {

    private static world planet = new world();
    
    private static AtomicInteger ground = new AtomicInteger(); 
    
    

    public static void main(String[] args) {

        ProcessBuilder Console = new ProcessBuilder("cmd", "/c", "start", "cmd", "/k", "java ConsoleInput.java");
        try {

            Console.start();
            utils.client();
        } catch (Exception e) { System.out.println("Errore console main: "+e.getMessage());}
        
    }
}