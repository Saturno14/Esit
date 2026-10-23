package src.gui;

import src.History;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.Locale;

/**
 * I morti, divisi per tipo di morte.
 *
 * AREE IMPILATE E NON TRE LINEE. Le cause sono parti di un totale: impilandole il bordo in alto
 * e' il numero dei morti e lo spessore di ogni fascia e' il contributo di quella causa, quindi
 * si legge in un colpo sia "quanti" sia "di cosa". Tre linee separate darebbero le stesse tre
 * serie ma costringerebbero a sommarle a occhio per sapere il totale.
 *
 * SONO CUMULATIVE, come i campioni che arrivano da History: una fascia che smette di crescere
 * dice che quella causa ha smesso di uccidere, e si vede subito perche' diventa orizzontale.
 */
class PannelloMorti extends JPanel {

    private final GraficoStorico grafico = new GraficoStorico(
            "Morti per causa (cumulative)", true,
            new GraficoStorico.Serie("fame", Tema.CAUSE[History.FAME], s -> s.morti[History.FAME]),
            new GraficoStorico.Serie("sete", Tema.CAUSE[History.SETE], s -> s.morti[History.SETE]),
            new GraficoStorico.Serie("combattimento", Tema.CAUSE[History.COMBATTIMENTO],
                    s -> s.morti[History.COMBATTIMENTO]));

    private final Targhetta tTotale = new Targhetta("Morti totali", Tema.MORTI);
    private final Targhetta[] tCausa = new Targhetta[History.CAUSE.length];

    PannelloMorti() {
        setLayout(new BorderLayout(0, 8));
        setBackground(Tema.SFONDO);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel targhette = new JPanel();
        targhette.setLayout(new BoxLayout(targhette, BoxLayout.X_AXIS));
        targhette.setBackground(Tema.SFONDO);
        targhette.add(tTotale);
        targhette.add(Box.createHorizontalStrut(8));
        for (int i = 0; i < History.CAUSE.length; i++) {
            tCausa[i] = new Targhetta(History.CAUSE[i], Tema.CAUSE[i]);
            targhette.add(tCausa[i]);
            targhette.add(Box.createHorizontalStrut(8));
        }
        targhette.add(Box.createHorizontalGlue());

        add(targhette, BorderLayout.NORTH);
        add(grafico, BorderLayout.CENTER);
    }

    void aggiorna() {
        grafico.aggiorna();
        int[] m = History.mortiPerCausa();
        int totale = m[0] + m[1] + m[2];
        tTotale.set(totale);
        for (int i = 0; i < m.length; i++) {
            // accanto al numero la quota sul totale: 300 morti di fame vogliono dire cose
            // diverse se i morti sono 320 o 3000
            tCausa[i].set(totale == 0 ? "0"
                    : String.format(Locale.ROOT, "%d  (%.0f%%)", m[i], 100.0 * m[i] / totale));
        }
    }
}
