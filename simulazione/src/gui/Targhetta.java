package src.gui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;

/**
 * Un numero con la sua etichetta: il "grafico" giusto per un valore singolo.
 *
 * Un totale (quanti morti di fame, quante nascite) non ha una forma: disegnarlo come una barra
 * alta quanto se stessa non aggiunge niente a leggerlo, e costringe a inventare una scala. Qui
 * il numero e' grande e scritto, e il colore accanto e' lo stesso che la serie ha nel grafico,
 * cosi' si collega a occhio senza bisogno di una legenda in piu'.
 */
class Targhetta extends JPanel {

    private final JLabel valore = new JLabel("-");

    /**
     * Larghezza minima della targhetta.
     *
     * Senza, la targhetta e' larga quanto il suo contenuto PIU' LUNGO al momento in cui viene
     * disposta: all'inizio il valore e' "-", quindi comanda l'etichetta; appena il numero
     * diventa "1 (100%)" comanda il numero e l'etichetta viene tagliata a meta' parola. Una
     * larghezza minima che tiene l'etichetta per intero toglie il problema alla radice, e le
     * targhette restano anche allineate fra loro.
     */
    private static final int LARGHEZZA_MINIMA = 120;

    Targhetta(String etichetta, Color colore) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Tema.PANNELLO);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, colore),
                BorderFactory.createEmptyBorder(6, 10, 6, 14)));

        JLabel nome = new JLabel(etichetta);
        nome.setFont(Tema.NORMALE);
        nome.setForeground(Tema.TESTO_DEBOLE);
        /*
         * L'etichetta si misura a mano e si tiene qualche pixel di margine.
         *
         * BoxLayout non allarga un componente oltre la sua dimensione MASSIMA, e per una JLabel
         * la massima e' la preferita: l'etichetta finisce larga esatta quanto il suo testo
         * misurato senza antialiasing, e al disegno - che l'antialiasing ce l'ha - l'ultima
         * lettera resta fuori per un paio di pixel. Si vedeva come "Nascite" scritto "Nascitc".
         */
        java.awt.FontMetrics fm = getFontMetrics(Tema.NORMALE);
        java.awt.Dimension misura =
                new java.awt.Dimension(fm.stringWidth(etichetta) + 8, fm.getHeight());
        nome.setPreferredSize(misura);
        nome.setMaximumSize(misura);
        nome.setAlignmentX(LEFT_ALIGNMENT);
        valore.setAlignmentX(LEFT_ALIGNMENT);

        valore.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        valore.setForeground(Tema.TESTO);

        add(nome);
        add(valore);

        // il bordo si mangia 4+10 a sinistra e 14 a destra: vanno aggiunti alla misura
        int larghezza = Math.max(LARGHEZZA_MINIMA, misura.width + 28);
        setMinimumSize(new java.awt.Dimension(larghezza, 54));
        setPreferredSize(new java.awt.Dimension(larghezza, 54));
        setMaximumSize(new java.awt.Dimension(larghezza, 54));
    }

    void set(String testo) {
        valore.setText(testo);
    }

    void set(long numero) {
        set(Long.toString(numero));
    }
}
