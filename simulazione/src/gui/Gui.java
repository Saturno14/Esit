package src.gui;

import javax.swing.SwingUtilities;

/**
 * La porta d'ingresso della GUI: tutto il resto del programma passa da qui.
 *
 * PERCHE' UNA FACCIATA STATICA. Chi apre la GUI e' il motore grafico, che gira sul thread
 * principale con il contesto OpenGL attaccato; chi la aggiorna e' il thread della simulazione;
 * chi la disegna e' il thread degli eventi di Swing. Se ognuno chiamasse direttamente i pannelli
 * ci si troverebbe prima o poi a costruire un JTable da un thread di simulazione, che e' il
 * genere di errore che non da' un'eccezione ma una finestra che si aggiorna a meta'. Qui dentro
 * c'e' l'unico punto in cui si passa il confine, e lo si passa sempre con invokeLater.
 *
 * SOSTITUISCE LA CONSOLE SU SOCKET. Prima i comandi si scrivevano in una finestra cmd separata
 * (ConsoleInput) che parlava con il motore via socket sulla porta 5443: due processi, un
 * protocollo a righe di testo e nessun modo di mostrare un grafico o una tabella. Ora la GUI sta
 * nello STESSO processo della simulazione, quindi legge Entity_manager, Genealogy e Flora
 * direttamente: niente protocollo da mantenere, e un clic su un'entita' nella scena 3D puo'
 * aprire la sua scheda perche' e' la stessa memoria.
 */
public final class Gui {

    private static ControlFrame frame;

    private Gui() {
    }

    /**
     * Vero quando la chiusura e' gia' stata decisa da qualcun altro (il motore grafico che si
     * sta spegnendo): serve a non far ripartire la chiusura da capo mentre e' in corso.
     */
    private static volatile boolean inChiusura = false;

    /** Apre la finestra di controllo (una sola: una seconda chiamata la porta davanti). */
    public static void start() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null && frame.isDisplayable()) {
                frame.toFront();
                return;
            }
            frame = new ControlFrame();
            frame.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    frame = null;
                    /*
                     * CHIUDERE LA FINESTRA DI CONTROLLO CHIUDE TUTTO.
                     *
                     * La simulazione gira su thread suoi, che non sono daemon: senza questo,
                     * chiusa la finestra il mondo continuava ad andare avanti - invisibile,
                     * ingovernabile e con il processo ancora vivo. Dato che e' da qui che si
                     * comanda la simulazione, chiudere questa finestra significa smettere, e
                     * allo stesso modo chiudere la finestra 3D chiude anche questa (vedi
                     * Main.main, che chiama stop()).
                     */
                    if (!inChiusura) {
                        chiudiTutto();
                    }
                }
            });
            frame.setVisible(true);
        });
    }

    /** Chiude la finestra: la chiama il motore grafico quando si chiude la finestra 3D. */
    public static void stop() {
        inChiusura = true;
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.dispose();
                frame = null;
            }
        });
    }

    /**
     * Ferma la simulazione e termina il processo.
     *
     * Si ferma prima il ciclo e si salva la genealogia, poi si esce: System.exit da solo
     * taglierebbe la sessione a meta' tick e l'albero genealogico resterebbe indietro di quello
     * che era successo dall'ultimo salvataggio automatico.
     */
    private static void chiudiTutto() {
        inChiusura = true;
        try {
            src.Simulation.stop();
            src.Genealogy.save();
        } catch (Throwable e) {
            System.out.println("Chiusura: " + e);
        }
        System.exit(0);
    }

    /**
     * L'entita' su cui si e' cliccato nella scena 3D, -1 se nessuna.
     *
     * PERCHE' IL CLIC NON APRE PIU' LA SCHEDA. Prima un clic faceva comparire subito una finestra
     * con tabelle e geni: per capire chi si era preso bisognava leggere un numero, e per guardare
     * un'altra entita' bisognava prima chiudere una finestra. Un clic adesso vuol dire soltanto
     * "questa qui": l'entita' si marca nel mondo (la vede il motore grafico, vedi
     * Renderer.disegnaIndicatore) e in fondo alla finestra di controllo compare la riga con il
     * tasto per aprirne la scheda. La scheda e' una SECONDA decisione, non una conseguenza.
     *
     * E' volatile perche' la scrive il thread degli eventi (o del rendering) e la legge il ciclo
     * di disegno a ogni frame.
     */
    private static volatile int selezionata = -1;

    /**
     * Segna un'entita' come selezionata: la marca nella scena e mostra la riga in basso.
     *
     * La chiama il clic nella scena 3D dal thread del rendering, quindi il passaggio a Swing
     * avviene qui e non la' - chi fa il picking non deve sapere niente di thread degli eventi.
     */
    public static void selezionaEntita(int id) {
        // se la finestra di controllo e' chiusa la si riapre: un clic su un'entita' nella scena
        // deve portare da qualche parte, non cadere nel vuoto
        selezionata = id;
        start();
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.mostraSelezione(id);
            }
        });
    }

    /** Toglie la selezione: via l'indicatore dal mondo e via la riga in basso. */
    public static void deseleziona() {
        selezionata = -1;
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.mostraSelezione(-1);
            }
        });
    }

    /** Chi e' selezionata adesso (-1 = nessuna). La legge il motore grafico a ogni frame. */
    public static int entitaSelezionata() {
        return selezionata;
    }

    /**
     * Apre (o porta davanti) la scheda di un'entita'. Da qui ci passano il tasto della riga di
     * selezione, il doppio clic nella lista e il comando "scheda <id>": aprire la scheda e'
     * sempre una richiesta esplicita.
     */
    public static void apriScheda(int id) {
        start();
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.apriEntita(id);
            }
        });
    }

    /** Mostra una riga nell'area dei messaggi della GUI, se e' aperta. */
    public static void messaggio(String testo) {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) {
                frame.messaggio(testo);
            }
        });
    }

    public static boolean isOpen() {
        return frame != null;
    }
}
