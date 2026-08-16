import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import src.*;
public class main {

    private static world planet = new world();
    
    private static AtomicInteger ground = new AtomicInteger(); 
    
    public static void client(){
        try {
            Socket socket = null;
            while (socket == null) {
                try {
                    socket = new Socket("localhost", 5443);
                } catch (Exception e) {
                    try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                    // ConsoleInput non è ancora pronto, riprova
                }
            }
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

                    } catch (Exception e) {System.out.println("Errore in server in"+e.getMessage());}
                }
            }).start();
        } catch (Exception e) {System.out.println("Errore in clint: "+e.getMessage());}


        
    }

    public static void main(String[] args) {

        ProcessBuilder Console = new ProcessBuilder("cmd", "/c", "start", "cmd", "/k", "java ConsoleInput.java");
        try {

            Console.start();
            client();
        } catch (Exception e) { System.out.println("Errore console main: "+e.getMessage());}
        
    }
}