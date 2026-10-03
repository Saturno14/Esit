package src;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class utils{
    public static String Clientoutput = "";

    public static void ClientSendMsg(String msg){
        Clientoutput = msg;
    }

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
                        String str = "";
                        str = in.readLine();
                        if(str == null){ System.out.println("Console chiusa"); break; }
                        if(!str.isBlank()){
                            System.out.println("report: "+str);
                            String result = console.input(str);
                            if(!result.isBlank()){
                                Clientoutput = result;
                            }
                        }
                        if(!Clientoutput.isBlank()){
                            out.println("Comand output: "+Clientoutput);
                            Clientoutput = "";
                        }
                        

                    } catch (Exception e) {System.out.println("Errore in server in"+e.getMessage());}
                }
            }).start();
        } catch (Exception e) {System.out.println("Errore in clint: "+e.getMessage());}
    }



    private static String Serverstr = "";
    /** Errori di lettura consecutivi dopo i quali la console si chiude da sola. */
    private static final int MAX_CONSECUTIVE_ERRORS = 5;
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
                int consecutiveErrors = 0;
                while(true){
                    try {
                        Serverstr = in.readLine();
                        if(Serverstr == null){ throw new java.io.IOException("connessione chiusa dal motore"); }
                        consecutiveErrors = 0; // lettura riuscita: la connessione e' viva
                        if(!Serverstr.isBlank()){System.out.println("report: "+Serverstr);}
                    } catch (Exception e) {
                        consecutiveErrors++;
                        System.out.println("Errore in server in "+e.getMessage());
                        // Il motore grafico si e' chiuso: la connessione cade e l'errore si ripete
                        // all'infinito. Dopo 5 errori di fila chiudo la console (con "cmd /c" la
                        // finestra si chiude insieme al processo).
                        if(consecutiveErrors >= MAX_CONSECUTIVE_ERRORS){
                            System.out.println("Motore grafico disconnesso: chiudo la console.");
                            System.exit(0);
                        }
                    }
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