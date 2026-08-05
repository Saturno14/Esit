import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Finestra non modale che mostra le statistiche stimate e lo storico azioni
 * dell'entita' selezionata cliccandoci sopra nel mondo. Si aggiorna quando
 * GameWindow chiama refresh(...) dal proprio timer.
 */
public class EntityInspectorDialog extends JDialog {

    private final JLabel idLabel = new JLabel();
    private final JLabel sexLabel = new JLabel();
    private final JLabel posLabel = new JLabel();
    private final JLabel stateLabel = new JLabel();
    private final JLabel fitnessLabel = new JLabel();
    private final JLabel rewardLabel = new JLabel();
    private final JLabel foodLabel = new JLabel();
    private final JLabel healthLabel = new JLabel();
    private final JTextArea logArea = new JTextArea();

    private int shownLogSize = -1;

    public EntityInspectorDialog(Window owner) {
        super(owner, "Dettagli entita'", ModalityType.MODELESS);
        setSize(420, 520);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

        JPanel header = new JPanel(new GridLayout(0, 1, 2, 2));
        header.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        Font f = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        for (JLabel l : new JLabel[]{idLabel, sexLabel, posLabel, stateLabel, fitnessLabel, rewardLabel, foodLabel, healthLabel}) {
            l.setFont(f);
            header.add(l);
        }
        idLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));

        JLabel disclaimer = new JLabel("<html><i>Fame/salute sono stimate ricostruendo la regola nota lato GUI, "
                + "non lette direttamente dal modello. Le azioni sono dedotte da posizione/reward.</i></html>");
        disclaimer.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        disclaimer.setForeground(new Color(110, 110, 110));
        disclaimer.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Storico azioni"));

        JPanel top = new JPanel(new BorderLayout());
        top.add(header, BorderLayout.CENTER);
        top.add(disclaimer, BorderLayout.SOUTH);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(logScroll, BorderLayout.CENTER);
    }

    /** Rinfresca la vista con lo stato corrente del tracker passato. Va chiamato dall'EDT. */
    public void refresh(EntityTracker t) {
        idLabel.setText("Entita' #" + t.id);
        sexLabel.setText("Sesso: " + (t.sex == 2 ? "femmina" : "maschio"));
        if (t.lastPos != null) {
            posLabel.setText(String.format("Posizione: (%d, %d, %d)", t.lastPos[0], t.lastPos[1], t.lastPos[2]));
        }
        stateLabel.setText("Stato: " + (t.deadLogged ? "deceduta" : "viva"));
        fitnessLabel.setText(String.format("Reward netto totale: %.2f", t.lastNetreward));
        rewardLabel.setText(""); // riservato per usi futuri, tenuto per allineare la griglia
        foodLabel.setText("Fame stimata: " + t.estFood + " / 100");
        healthLabel.setText("Salute stimata: " + t.estHealth + " / 100");

        List<EntityTracker.LogEntry> entries = t.getLog();
        if (entries.size() != shownLogSize) {
            StringBuilder sb = new StringBuilder();
            for (EntityTracker.LogEntry e : entries) {
                sb.append('[').append(e.time).append("] ").append(e.text).append('\n');
            }
            logArea.setText(sb.toString());
            logArea.setCaretPosition(logArea.getDocument().getLength());
            shownLogSize = entries.size();
        }
    }
}
