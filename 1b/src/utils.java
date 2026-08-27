package src;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class utils{
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

    private static String Serverstr = "";
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
                        Serverstr = in.readLine();
                        if(!Serverstr.isBlank()){System.out.println("report: "+Serverstr);}
                        

                    } catch (Exception e) {System.out.println("Errore in server in "+e.getMessage());}
                }
            }).start();

            System.out.println("Inserisci comando: ");
            while (true) { 
                String comand = scanner.nextLine();
                out.println(comand);
            }

        } catch (Exception e) {System.out.println("Errore Server: "+e.getMessage());}
    }

}