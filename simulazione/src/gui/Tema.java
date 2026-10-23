package src.gui;

import java.awt.Color;
import java.awt.Font;

/**
 * Colori e caratteri della GUI, in un posto solo.
 *
 * I COLORI DELLE SERIE SONO FISSI E ORDINATI. "fame" e' sempre il primo colore, "sete" il
 * secondo, "combattimento" il terzo, in tutti i grafici e in tutte le legende: se il colore
 * dipendesse da quante serie sono visibili, togliere una causa dal grafico ripingerebbe le altre
 * e due schermate dello stesso dato si leggerebbero come due dati diversi. Le tre tinte sono
 * distinguibili anche da chi non vede bene il rosso e il verde (blu / arancio / viola), e la
 * legenda scrive comunque il nome accanto al colore, quindi il colore non e' mai l'unica
 * informazione.
 */
final class Tema {

    static final Color SFONDO = new Color(0xF7F8FA);
    static final Color PANNELLO = Color.WHITE;
    static final Color GRIGLIA = new Color(0xE3E6EB);
    static final Color ASSE = new Color(0x9AA3AF);
    static final Color TESTO = new Color(0x1F2430);
    static final Color TESTO_DEBOLE = new Color(0x6B7280);

    /** Popolazione viva. */
    static final Color POPOLAZIONE = new Color(0x2A78D6);
    /** Nascite cumulative. */
    static final Color NASCITE = new Color(0x1E8E5A);
    /** Morti totali cumulative. */
    static final Color MORTI = new Color(0x4B5563);

    /** Le cause di morte, nello stesso ordine di History.CAUSE. */
    static final Color[] CAUSE = {
            new Color(0x2A78D6), // fame
            new Color(0xEB6834), // sete
            new Color(0x7B5EA7), // combattimento
    };

    static final Color VIVA = new Color(0x1E8E5A);
    static final Color MORTA = new Color(0xB02A1F);

    static final Font NORMALE = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    static final Font GRASSETTO = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    static final Font TITOLO = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private Tema() {
    }
}
