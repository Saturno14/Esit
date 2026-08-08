
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class ConsoleInput {
    private static String str = "";
    public static void server(){
        try {
            ServerSocket server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(5443));
            Socket client = server.accept();
            System.out.println("Client collegato");

            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(client.getOutputStream(), true);

            Scanner scanner = new Scanner(System.in);

            new Thread(()->{
                while(true){
                    try {
                        str = in.readLine();
                        if(!str.isBlank()){System.out.println("report: "+str);}
                        

                    } catch (Exception e) {System.out.println("Errore in server in");}
                }
            }).start();

            System.out.println("Inserisci comando: ");
            while (true) { 
                String comand = scanner.nextLine();
                out.println(comand);
            }

        } catch (Exception e) {System.out.println("Errore Server: "+e.getMessage());}
    }
    public static void main(String[] args) {
        server();

    }
}
