package src.gui;

import src.Commands;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * La riga di comando della GUI: quello che prima era la finestra cmd separata.
 *
 * I COMANDI NON SONO SCRITTI QUI. L'elenco a sinistra, il completamento col tasto Tab e
 * l'esecuzione passano tutti da {@link Commands}, che e' l'unico posto dove un comando esiste:
 * aggiungerne uno la' lo fa comparire qui dentro senza toccare questo file. Il motivo e' lo
 * stesso per cui i comandi sono stati riscritti come registro: due elenchi della stessa cosa
 * divergono sempre.
 *
 * I COMANDI GIRANO SU UN ALTRO THREAD. "newstart" ricrea il mondo e le fondatrici, "load"
 * legge dei file: eseguirli sul thread degli eventi di Swing bloccherebbe la finestra per tutto
 * il tempo (niente ridisegno, niente grafici, l'aria di programma piantato). Vanno in un thread
 * a parte e il risultato torna nell'area del testo con invokeLater.
 */
class PannelloComandi extends JPanel {

    private final JTextArea uscita = new JTextArea();
    private final JTextField riga = new JTextField();
    private final List<String> storico = new ArrayList<>();
    private int posizioneStorico = 0;

    PannelloComandi() {
        setLayout(new BorderLayout());
        setBackground(Tema.SFONDO);

        uscita.setEditable(false);
        uscita.setFont(Tema.MONO);
        uscita.setBackground(Tema.PANNELLO);
        uscita.setLineWrap(false);

        riga.setFont(Tema.MONO);
        riga.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.GRIGLIA),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));

        JPanel basso = new JPanel(new BorderLayout(6, 0));
        basso.setBackground(Tema.SFONDO);
        basso.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        JLabel prompt = new JLabel("  > ");
        prompt.setFont(Tema.MONO);
        JButton invia = new JButton("Invia");
        invia.addActionListener(a -> esegui(riga.getText()));
        basso.add(prompt, BorderLayout.WEST);
        basso.add(riga, BorderLayout.CENTER);
        basso.add(invia, BorderLayout.EAST);

        JPanel destra = new JPanel(new BorderLayout());
        destra.setBackground(Tema.SFONDO);
        destra.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        destra.add(new JScrollPane(uscita), BorderLayout.CENTER);
        destra.add(basso, BorderLayout.SOUTH);

        JSplitPane divisore = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, elenco(), destra);
        divisore.setDividerLocation(280);
        divisore.setResizeWeight(0);
        add(divisore, BorderLayout.CENTER);

        tasti();
        scrivi("Comandi della simulazione. Scrivi help per l'elenco, Tab per completare, "
                + "freccia su per i comandi precedenti.");
        scrivi(Commands.run("help"));
    }

    /** L'elenco dei comandi: clic per metterlo nella riga, doppio clic per eseguirlo subito. */
    private JPanel elenco() {
        DefaultListModel<Object> modello = new DefaultListModel<>();
        String categoria = null;
        for (Commands.Command c : Commands.all()) {
            if (!c.category.equals(categoria)) {
                categoria = c.category;
                modello.addElement("[" + categoria + "]");
            }
            modello.addElement(c);
        }

        JList<Object> lista = new JList<>(modello);
        lista.setFont(Tema.NORMALE);
        lista.setBackground(Tema.PANNELLO);
        lista.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i,
                                                          boolean sel, boolean fuoco) {
                super.getListCellRendererComponent(l, v, i, sel, fuoco);
                if (v instanceof Commands.Command c) {
                    setText("   " + c.name);
                    setToolTipText(c.usage + " - " + c.description);
                    setFont(Tema.NORMALE);
                } else {
                    setText(String.valueOf(v));
                    setFont(Tema.GRASSETTO);
                    if (!sel) {
                        setForeground(Tema.TESTO_DEBOLE);
                    }
                }
                return this;
            }
        });
        lista.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (!(lista.getSelectedValue() instanceof Commands.Command c)) {
                    return;
                }
                if (e.getClickCount() >= 2) {
                    esegui(c.name);
                } else {
                    // l'uso senza il nome: "speed <fattore>" diventa "speed " e il cursore resta
                    // dove si deve scrivere l'argomento
                    riga.setText(c.usage.contains("<") ? c.name + " " : c.name);
                    riga.requestFocusInWindow();
                    riga.setCaretPosition(riga.getText().length());
                }
            }
        });

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Tema.SFONDO);
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
        JLabel titolo = new JLabel("Comandi");
        titolo.setFont(Tema.TITOLO);
        titolo.setBorder(BorderFactory.createEmptyBorder(0, 2, 6, 0));
        p.add(titolo, BorderLayout.NORTH);
        p.add(new JScrollPane(lista), BorderLayout.CENTER);

        JPanel azioni = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        azioni.setBackground(Tema.SFONDO);
        JButton pulisci = new JButton("Pulisci");
        pulisci.addActionListener(a -> uscita.setText(""));
        azioni.add(pulisci);
        p.add(azioni, BorderLayout.SOUTH);
        return p;
    }

    private void tasti() {
        var im = riga.getInputMap();
        var am = riga.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "invia");
        am.put("invia", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                esegui(riga.getText());
            }
        });

        // Tab completa invece di spostare il fuoco: in una riga di comando il tab e' il
        // completamento, e il fuoco si sposta col mouse o con Ctrl+Tab.
        riga.setFocusTraversalKeysEnabled(false);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "completa");
        am.put("completa", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                completa();
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "indietro");
        am.put("indietro", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                muoviStorico(-1);
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "avanti");
        am.put("avanti", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                muoviStorico(1);
            }
        });
    }

    private void completa() {
        String testo = riga.getText();
        if (testo.contains(" ")) {
            return; // si completa il nome del comando, non gli argomenti
        }
        List<String> possibili = Commands.complete(testo);
        if (possibili.isEmpty()) {
            return;
        }
        if (possibili.size() == 1) {
            riga.setText(possibili.get(0) + " ");
            riga.setCaretPosition(riga.getText().length());
            return;
        }
        // piu' di un candidato: si completa il pezzo comune e si mostrano le alternative
        String comune = possibili.get(0);
        for (String p : possibili) {
            int i = 0;
            while (i < comune.length() && i < p.length() && comune.charAt(i) == p.charAt(i)) {
                i++;
            }
            comune = comune.substring(0, i);
        }
        if (comune.length() > testo.length()) {
            riga.setText(comune);
            riga.setCaretPosition(comune.length());
        }
        scrivi(String.join("   ", possibili));
    }

    private void muoviStorico(int passo) {
        if (storico.isEmpty()) {
            return;
        }
        posizioneStorico = Math.max(0, Math.min(storico.size(), posizioneStorico + passo));
        riga.setText(posizioneStorico == storico.size() ? "" : storico.get(posizioneStorico));
        riga.setCaretPosition(riga.getText().length());
    }

    private void esegui(String comando) {
        String c = comando.trim();
        if (c.isEmpty()) {
            return;
        }
        riga.setText("");
        if (storico.isEmpty() || !storico.get(storico.size() - 1).equals(c)) {
            storico.add(c);
        }
        posizioneStorico = storico.size();
        scrivi("> " + c);

        Thread t = new Thread(() -> {
            String risposta;
            try {
                risposta = Commands.run(c);
            } catch (Throwable e) {
                // Commands.run non dovrebbe lanciare niente, ma se lo fa l'errore si legge qui
                // invece di sparire in un thread senza nessuno che lo guarda
                risposta = "Errore interno: " + e;
            }
            String finale = risposta;
            SwingUtilities.invokeLater(() -> scrivi(finale));
        }, "esit-comando");
        t.setDaemon(true);
        t.start();
    }

    /** Aggiunge testo in fondo e ci scorre: da chiamare dal thread degli eventi. */
    void scrivi(String testo) {
        if (testo == null || testo.isBlank()) {
            return;
        }
        uscita.append(testo.stripTrailing() + "\n");
        uscita.setCaretPosition(uscita.getDocument().getLength());
    }
}
