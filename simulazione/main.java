/**
 * Avvio della simulazione SENZA motore grafico: si apre solo la finestra di controllo.
 *
 * Serve a far girare la simulazione su una macchina senza OpenGL, o quando interessano i numeri
 * e non l'immagine: la finestra ha le liste, i grafici e i comandi, quindi da qui si fa tutto
 * quello che si faceva dalla vecchia console di testo.
 *
 * COSA C'ERA PRIMA. Questo main apriva una finestra cmd (ConsoleInput) e le si collegava via
 * socket sulla porta 5443: due processi che si scambiavano righe di testo. Serviva perche' la
 * console era testuale e doveva avere il suo terminale; una GUI no, vive nello stesso processo
 * della simulazione, e cosi' legge i dati direttamente invece di chiederli a un protocollo.
 * Il mondo lo crea il comando "newstart" dalla GUI, come nel motore grafico.
 */
public class main {

    public static void main(String[] args) {
        src.gui.Gui.start();
    }
}
