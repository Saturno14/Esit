package src.gui;

import src.DayNight;
import src.Entity_manager;
import src.Flora;
import src.Simulation;
import src.world;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.HeadlessException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * La finestra di controllo: liste, grafici e comandi, piu' la riga di stato.
 *
 * SI AGGIORNA SOLO QUELLO CHE SI VEDE. Un timer unico, due volte al secondo, aggiorna la riga di
 * stato e SOLO la scheda in primo piano. Aggiornare tutto vorrebbe dire ricostruire due tabelle
 * da qualche centinaio di righe e due grafici anche quando si sta guardando i comandi: lavoro
 * buttato, e proprio sul thread che deve restare reattivo. Le schede nascoste si aggiornano
 * quando si aprono, che e' l'unico momento in cui qualcuno le guarda.
 *
 * DUE VOLTE AL SECONDO, NON A OGNI TICK. La simulazione a velocita' alta fa molti tick al
 * secondo; l'occhio non legge una tabella che cambia dieci volte in un secondo. Il periodo e'
 * scelto per essere leggibile, non per essere fedele all'ultimo tick: i grafici hanno comunque
 * tutti i campioni, perche' quelli li prende History dentro la simulazione.
 */
class ControlFrame extends JFrame {

    private static final int PERIODO_MS = 500;

    private final JTabbedPane schede = new JTabbedPane();
    private final PannelloEntita entita = new PannelloEntita(this::apriEntita);
    private final PannelloPopolazione popolazione = new PannelloPopolazione();
    private final PannelloMorti morti = new PannelloMorti();
    private final PannelloComandi comandi = new PannelloComandi();
    private final JLabel stato = new JLabel(" ");

    /**
     * LA RIGA DELLA SELEZIONE. Un clic nella scena 3D non apre piu' una finestra: marca l'entita'
     * nel mondo e accende questa riga, che dice chi e' e offre il tasto per la scheda. Resta
     * nascosta finche' non c'e' niente di selezionato, cosi' non occupa spazio per niente e la
     * sua comparsa e' essa stessa il segnale che il clic e' andato a buon fine.
     */
    private final JPanel barraSelezione = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 4));
    private final JLabel etichettaSelezione = new JLabel();
    private int selezionata = -1;

    /** Una scheda per entita', riusata: due clic sulla stessa entita' non aprono due finestre. */
    private final Map<Integer, SchedaEntita> aperte = new HashMap<>();

    ControlFrame() {
        super("Esit - Controllo simulazione");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1120, 700);
        setLocationByPlatform(true);

        schede.setFont(Tema.NORMALE);
        schede.addTab("Entita'", entita);
        schede.addTab("Popolazione", popolazione);
        schede.addTab("Morti", morti);
        schede.addTab("Comandi", comandi);
        schede.addChangeListener(e -> aggiornaVisibile());

        stato.setFont(Tema.MONO);
        stato.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Tema.GRIGLIA),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));

        barraSelezione.setBackground(Tema.SFONDO);
        barraSelezione.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Tema.GRIGLIA));
        etichettaSelezione.setFont(Tema.GRASSETTO);
        barraSelezione.add(etichettaSelezione);
        javax.swing.JButton apri = new javax.swing.JButton("Apri scheda");
        apri.setFont(Tema.NORMALE);
        apri.addActionListener(a -> {
            if (selezionata >= 0) {
                apriEntita(selezionata);
            }
        });
        barraSelezione.add(apri);
        javax.swing.JButton togli = new javax.swing.JButton("Deseleziona");
        togli.setFont(Tema.NORMALE);
        togli.addActionListener(a -> src.gui.Gui.deseleziona());
        barraSelezione.add(togli);
        barraSelezione.setVisible(false);

        // la riga di selezione sta SOPRA quella di stato: lo stato e' il fondo fisso della
        // finestra, la selezione e' una cosa che compare e sparisce
        JPanel basso = new JPanel(new BorderLayout());
        basso.setBackground(Tema.SFONDO);
        basso.add(barraSelezione, BorderLayout.NORTH);
        basso.add(stato, BorderLayout.SOUTH);

        JPanel radice = new JPanel(new BorderLayout());
        radice.setBackground(Tema.SFONDO);
        radice.add(schede, BorderLayout.CENTER);
        radice.add(basso, BorderLayout.SOUTH);
        setContentPane(radice);

        Timer timer = new Timer(PERIODO_MS, a -> {
            aggiornaStato();
            aggiornaVisibile();
        });
        timer.start();
        // chiudendo la finestra si ferma anche il timer: senza questo resterebbe a interrogare
        // la simulazione per niente finche' il processo vive
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                timer.stop();
            }
        });
    }

    private void aggiornaVisibile() {
        java.awt.Component c = schede.getSelectedComponent();
        if (c == entita) {
            entita.aggiorna();
        } else if (c == popolazione) {
            popolazione.aggiorna();
        } else if (c == morti) {
            morti.aggiorna();
        }
    }

    private void aggiornaStato() {
        StringBuilder sb = new StringBuilder();
        sb.append("tick ").append(Simulation.getTick());
        sb.append("   ").append(Simulation.isRunning()
                ? (world.isPaused() ? "in pausa" : "in esecuzione") : "ferma");
        sb.append("   x").append(Simulation.getTimeScale());
        sb.append("   seed ").append(Simulation.getSeed());
        sb.append("   vive ").append(Entity_manager.Entity_count())
          .append('/').append(Simulation.maxPopulation());
        sb.append("   mele ").append(world.countApples());
        if (Flora.enabled) {
            sb.append(" (").append(Flora.countOnTree()).append(" sugli alberi)")
              .append("   alberi ").append(Flora.countTrees());
        }
        sb.append("   ").append(DayNight.phaseName())
          .append(String.format(Locale.ROOT, " (luce %.2f)", DayNight.daylight()));
        stato.setText(sb.toString());
        if (selezionata >= 0 && barraSelezione.isVisible()) {
            aggiornaEtichettaSelezione();
        }
    }

    /** Apre la scheda di un'entita' e seleziona la sua riga nelle liste. */
    void apriEntita(int id) {
        SchedaEntita s = aperte.get(id);
        if (s != null && s.isDisplayable()) {
            s.toFront();
            s.requestFocus();
            return;
        }
        try {
            s = new SchedaEntita(id);
        } catch (HeadlessException e) {
            return;
        }
        aperte.put(id, s);
        SchedaEntita finale = s;
        s.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                aperte.remove(finale.getEntityId(), finale);
            }
        });
        s.setLocationRelativeTo(this);
        s.setVisible(true);
        entita.seleziona(id);
    }

    /**
     * Mostra (o nasconde, con id negativo) la riga della selezione, e porta in vista la riga
     * dell'entita' nella lista: cliccare nel mondo e cliccare nell'elenco devono lasciare la
     * finestra nello stesso stato.
     */
    void mostraSelezione(int id) {
        selezionata = id;
        if (id < 0) {
            barraSelezione.setVisible(false);
            revalidate();
            return;
        }
        aggiornaEtichettaSelezione();
        barraSelezione.setVisible(true);
        revalidate();
        entita.seleziona(id);
    }

    /**
     * Rinfresca il testo della riga, senza toccare la selezione della lista: la riga invecchia
     * (cibo, acqua, eta' cambiano, e un'entita' selezionata puo' morire mentre si guarda) ma
     * riselezionare la tabella due volte al secondo strapperebbe di mano la riga a chi sta
     * scorrendo l'elenco.
     */
    private void aggiornaEtichettaSelezione() {
        src.entity e = SchedaEntita.cerca(selezionata);
        StringBuilder sb = new StringBuilder();
        sb.append("Selezionata: entita' #").append(selezionata);
        if (e == null) {
            sb.append("   (non e' piu' in memoria)");
        } else {
            sb.append("   ").append(e.isAlive() ? "viva" : "morta")
              .append("   gen ").append(e.getGenome().lineage().generation())
              .append("   eta' ").append(e.getAge())
              .append("   ").append(e.getSex() == 2 ? "F" : "M")
              .append("   cibo ").append(e.getFood())
              .append("   acqua ").append(e.getThirst());
        }
        etichettaSelezione.setText(sb.toString());
    }

    /** Una riga nell'area dei comandi: ci finiscono anche i messaggi del motore grafico. */
    void messaggio(String testo) {
        comandi.scrivi(testo);
    }
}
