package src.gui;

import src.Entity_manager;
import src.Genealogy;
import src.entity;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * La lista delle entita': una sola pagina, con il filtro di stato.
 *
 * PERCHE' UNA PAGINA E NON DUE. Prima c'erano due schede, "Tutte" e "Vive", con la stessa
 * tabella e lo stesso contenuto a meno di una riga di codice. Due schede identiche costringono a
 * cercare in quale delle due si era, e a cambiare scheda per rispondere a "questa e' ancora
 * viva?" - che e' una domanda sulla STESSA lista, non su un'altra lista. Adesso lo stato e' un
 * filtro come gli altri: tutte, solo vive, solo morte.
 *
 * LA SELEZIONE SOPRAVVIVE AL RINFRESCO. La tabella si ricarica da sola due volte al secondo:
 * senza accorgimenti, la riga selezionata salterebbe di posto (o si perderebbe) ogni volta che
 * un'entita' nasce o muore. Si ricorda quindi l'ID selezionato, non il numero di riga, e dopo
 * il rinfresco si va a cercare dove e' finito.
 */
class PannelloEntita extends JPanel {

    private static final String[] COLONNE = {
            "id", "stato", "gen", "eta'", "sesso", "dieta", "salute", "cibo", "acqua",
            "mele", "uccisioni", "figli", "fine"
    };

    /** Le scelte del filtro di stato, nell'ordine in cui compaiono nel menu. */
    private static final String[] STATI = {"tutte", "solo vive", "solo morte"};
    private static final int TUTTE = 0;
    private static final int VIVE = 1;
    private static final int MORTE = 2;

    private final javax.swing.JComboBox<String> stato = new javax.swing.JComboBox<>(STATI);
    private final Modello modello = new Modello();
    private final JTable tabella = new JTable(modello);
    private final TableRowSorter<Modello> ordinatore = new TableRowSorter<>(modello);
    private final JTextField filtro = new JTextField(14);
    private final JCheckBox congela = new JCheckBox("congela elenco");
    private final JLabel conteggio = new JLabel();

    PannelloEntita(IntConsumer apri) {
        setLayout(new BorderLayout(0, 6));
        setBackground(Tema.SFONDO);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        tabella.setRowSorter(ordinatore);
        tabella.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabella.setFont(Tema.NORMALE);
        tabella.setRowHeight(20);
        tabella.getTableHeader().setFont(Tema.GRASSETTO);
        tabella.setAutoCreateRowSorter(false);
        tabella.setDefaultRenderer(Object.class, new Celle());
        tabella.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int id = idSelezionato();
                    if (id >= 0) {
                        apri.accept(id);
                    }
                }
            }
        });

        JPanel alto = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        alto.setBackground(Tema.SFONDO);
        alto.add(new JLabel("Stato:"));
        stato.setFont(Tema.NORMALE);
        // cambiando stato la lista si rifa' subito: aspettare il prossimo giro del timer
        // darebbe mezzo secondo di tabella che non corrisponde a quello che dice il menu
        stato.addActionListener(a -> ricarica());
        alto.add(stato);
        alto.add(new JLabel("   Filtro:"));
        alto.add(filtro);
        JButton bottone = new JButton("Apri scheda");
        bottone.addActionListener(a -> {
            int id = idSelezionato();
            if (id >= 0) {
                apri.accept(id);
            }
        });
        alto.add(bottone);
        alto.add(congela);
        conteggio.setForeground(Tema.TESTO_DEBOLE);
        alto.add(conteggio);

        filtro.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applicaFiltro(); }
            @Override public void removeUpdate(DocumentEvent e) { applicaFiltro(); }
            @Override public void changedUpdate(DocumentEvent e) { applicaFiltro(); }
        });

        add(alto, BorderLayout.NORTH);
        add(new JScrollPane(tabella), BorderLayout.CENTER);
        // l'ordine di partenza: le piu' recenti in cima, cioe' gli id piu' alti
        ordinatore.toggleSortOrder(0);
        ordinatore.toggleSortOrder(0);
    }

    private void applicaFiltro() {
        String t = filtro.getText().trim();
        if (t.isEmpty()) {
            ordinatore.setRowFilter(null);
        } else {
            // (?i) = senza distinzione di maiuscole; Pattern.quote perche' chi filtra scrive un
            // testo, non un'espressione regolare, e un "(" battuto per sbaglio non deve esplodere
            ordinatore.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(t)));
        }
    }

    /** L'id dell'entita' selezionata, -1 se non c'e' selezione. */
    int idSelezionato() {
        int riga = tabella.getSelectedRow();
        if (riga < 0) {
            return -1;
        }
        return (Integer) modello.getValueAt(tabella.convertRowIndexToModel(riga), 0);
    }

    /** Seleziona la riga di quell'id e la porta in vista. */
    void seleziona(int id) {
        for (int i = 0; i < modello.getRowCount(); i++) {
            if ((Integer) modello.getValueAt(i, 0) == id) {
                int vista = tabella.convertRowIndexToView(i);
                if (vista >= 0) {
                    tabella.setRowSelectionInterval(vista, vista);
                    tabella.scrollRectToVisible(tabella.getCellRect(vista, 0, true));
                }
                return;
            }
        }
    }

    void aggiorna() {
        if (congela.isSelected()) {
            return;
        }
        ricarica();
    }

    /** Rilegge le entita' secondo lo stato scelto, tenendo la selezione. */
    private void ricarica() {
        int selezionato = idSelezionato();
        int scelta = stato.getSelectedIndex();
        List<entity> elenco;
        if (scelta == VIVE) {
            elenco = Entity_manager.snapshotAlive();
        } else {
            elenco = new ArrayList<>(Entity_manager.snapshotAll());
            if (scelta == MORTE) {
                elenco.removeIf(entity::isAlive);
            }
        }
        modello.ricarica(elenco);
        conteggio.setText("  " + modello.getRowCount() + " "
                + (scelta == TUTTE ? "in tutto" : scelta == VIVE ? "vive" : "morte"));
        if (selezionato >= 0) {
            seleziona(selezionato);
        }
    }

    /** Colora lo stato: verde se viva, rosso se morta. Il testo dice comunque quale dei due. */
    private static final class Celle extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                       boolean fuoco, int riga, int col) {
            Component c = super.getTableCellRendererComponent(t, v, sel, fuoco, riga, col);
            setHorizontalAlignment(v instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
            if (!sel) {
                boolean viva = "viva".equals(t.getValueAt(riga, 1));
                setForeground(col == 1 ? (viva ? Tema.VIVA : Tema.MORTA) : Tema.TESTO);
            }
            return c;
        }
    }

    /**
     * Le righe sono una fotografia: si copia l'elenco delle entita' e si legge da quello.
     * Leggere direttamente da Entity_manager mentre Swing ridisegna vorrebbe dire che una riga
     * puo' sparire tra il momento in cui si chiede quante sono e quello in cui si chiede la
     * terza, e la tabella lancerebbe un'eccezione di indice dentro al disegno.
     */
    private static final class Modello extends AbstractTableModel {

        private List<Object[]> righe = new ArrayList<>();

        void ricarica(List<entity> entita) {
            List<Object[]> nuove = new ArrayList<>(entita.size());
            java.util.Map<Integer, Integer> figli = Genealogy.conteggioFigli();
            for (entity e : entita) {
                nuove.add(new Object[]{
                        e.getId(),
                        e.isAlive() ? "viva" : "morta",
                        e.getGenome().lineage().generation(),
                        e.getAge(),
                        e.getSex() == 2 ? "F" : "M",
                        String.valueOf(e.dietNow()).toLowerCase(Locale.ROOT),
                        e.getHealt(),
                        e.getFood(),
                        e.getThirst(),
                        e.getApplesEaten(),
                        e.getKills(),
                        figli.getOrDefault(e.getId(), 0),
                        e.isAlive() ? "" : e.getDeathCause() + " (tick " + e.getDeathTick() + ")"
                });
            }
            righe = nuove;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return righe.size(); }
        @Override public int getColumnCount() { return COLONNE.length; }
        @Override public String getColumnName(int c) { return COLONNE[c]; }
        @Override public Object getValueAt(int r, int c) { return righe.get(r)[c]; }

        @Override
        public Class<?> getColumnClass(int c) {
            // serve all'ordinatore: senza questo "10" starebbe prima di "9" perche' le
            // confronterebbe come stringhe
            return switch (c) {
                case 0, 2, 3, 6, 7, 8, 9, 10, 11 -> Integer.class;
                default -> String.class;
            };
        }
    }
}
