package src.gui;

import src.Entity_manager;
import src.Genealogy;
import src.entity;
import src.genetics.GeneDefinition;
import src.genetics.GenomeSchema;
import src.genetics.PhenotypeResolver;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * La scheda di una singola entita': statistiche, geni e albero genealogico.
 *
 * TIENE L'ID, NON L'OGGETTO. Se la scheda tenesse il riferimento all'entita', continuerebbe a
 * mostrare i suoi numeri anche dopo che la simulazione l'ha tolta dalla lista dei vivi - e anche
 * dopo un Load, dove le entita' vengono ricostruite da zero e quelle vecchie restano in memoria
 * solo perche' questa finestra le tiene. Tenendo l'id, ogni rinfresco cerca l'entita' adesso: se
 * non c'e' piu' la scheda lo dice, invece di mostrare una fotografia vecchia spacciandola per lo
 * stato corrente.
 *
 * SI AGGIORNA SOLO SE E' APERTA. Il timer parte con la finestra e si ferma alla chiusura:
 * dieci schede aperte e dimenticate non devono continuare a interrogare la simulazione.
 */
class SchedaEntita extends JFrame {

    private final int id;
    private final JLabel intestazione = new JLabel();
    private final ModelloCoppie statistiche = new ModelloCoppie();
    private final ModelloGeni geni = new ModelloGeni();
    private final Barre barre = new Barre();
    private final JTextArea corpo = new JTextArea();
    private final DefaultMutableTreeNode radice = new DefaultMutableTreeNode("albero");
    private final DefaultTreeModel modelloAlbero = new DefaultTreeModel(radice);
    private final JTree albero = new JTree(modelloAlbero);
    private final Timer timer;

    SchedaEntita(int id) {
        super("Entita' #" + id);
        this.id = id;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(900, 620);

        intestazione.setFont(Tema.TITOLO);
        intestazione.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JTabbedPane schede = new JTabbedPane();
        schede.setFont(Tema.NORMALE);
        schede.addTab("Statistiche", pannelloStatistiche());
        schede.addTab("Geni", new JScrollPane(tabellaGeni()));
        schede.addTab("Corpo", pannelloCorpo());
        schede.addTab("Albero genealogico", pannelloAlbero());

        JPanel radicePannello = new JPanel(new BorderLayout());
        radicePannello.setBackground(Tema.SFONDO);
        radicePannello.add(intestazione, BorderLayout.NORTH);
        radicePannello.add(schede, BorderLayout.CENTER);
        setContentPane(radicePannello);

        aggiorna();
        costruisciAlbero();

        timer = new Timer(500, a -> aggiorna());
        timer.start();
    }

    int getEntityId() {
        return id;
    }

    @Override
    public void dispose() {
        if (timer != null) {
            timer.stop();
        }
        super.dispose();
    }

    private static JTable tabella(AbstractTableModel m) {
        JTable t = new JTable(m);
        t.setFont(Tema.NORMALE);
        t.setRowHeight(20);
        t.getTableHeader().setFont(Tema.GRASSETTO);
        t.setAutoCreateRowSorter(true);
        return t;
    }

    /**
     * La tabella dei geni, con le colonne dei nomi piu' larghe.
     *
     * Le larghezze di partenza sono tutte uguali, e un nome come "body.limb_thickness" ci finisce
     * tagliato a meta': un gene che non si legge non si confronta con quello dell'altra entita',
     * che e' l'unica cosa per cui si apre questa pagina.
     */
    private JTable tabellaGeni() {
        JTable t = tabella(geni);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        t.getColumnModel().getColumn(0).setPreferredWidth(190);
        for (int c = 1; c <= 5; c++) {
            t.getColumnModel().getColumn(c).setPreferredWidth(80);
        }
        t.getColumnModel().getColumn(6).setPreferredWidth(200);
        return t;
    }

    private JPanel pannelloStatistiche() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(Tema.SFONDO);
        p.add(barre, BorderLayout.NORTH);
        p.add(new JScrollPane(tabella(statistiche)), BorderLayout.CENTER);
        return p;
    }

    private JPanel pannelloCorpo() {
        corpo.setEditable(false);
        corpo.setFont(Tema.MONO);
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JScrollPane(corpo), BorderLayout.CENTER);
        return p;
    }

    private JPanel pannelloAlbero() {
        albero.setFont(Tema.NORMALE);
        albero.setRootVisible(false);
        albero.setShowsRootHandles(true);
        JPanel p = new JPanel(new BorderLayout(0, 4));
        JLabel nota = new JLabel("  Doppio clic su un parente per aprire la sua scheda.");
        nota.setForeground(Tema.TESTO_DEBOLE);
        nota.setFont(Tema.NORMALE);
        p.add(nota, BorderLayout.NORTH);
        p.add(new JScrollPane(albero), BorderLayout.CENTER);
        albero.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                TreePath path = albero.getPathForLocation(e.getX(), e.getY());
                if (path == null) {
                    return;
                }
                Object o = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                if (o instanceof Parente parente) {
                    Gui.selezionaEntita(parente.id());
                }
            }
        });
        return p;
    }

    // ------------------------------------------------------------------ rinfresco

    static entity cerca(int id) {
        for (entity e : Entity_manager.snapshotAll()) {
            if (e.getId() == id) {
                return e;
            }
        }
        return null;
    }

    private void aggiorna() {
        entity e = cerca(id);
        if (e == null) {
            intestazione.setText("Entita' #" + id + " - non e' piu' in memoria (nuova sessione?)");
            statistiche.set(List.of());
            barre.set(null);
            return;
        }

        intestazione.setText("Entita' #" + id
                + (e.isAlive() ? "  -  viva"
                        : "  -  morta: " + e.getDeathCause() + " al tick " + e.getDeathTick())
                + "  -  generazione " + e.getGenome().lineage().generation());
        intestazione.setForeground(e.isAlive() ? Tema.VIVA : Tema.MORTA);

        var st = e.getStats();
        int[] pos = e.getPos();
        List<String[]> righe = new ArrayList<>();
        righe.add(new String[]{"eta'", e.getAge() + " passi"});
        righe.add(new String[]{"nata al ciclo", String.valueOf(e.getBorn())});
        righe.add(new String[]{"sesso", e.getSex() == 2 ? "femmina" : "maschio"});
        righe.add(new String[]{"dieta", String.format(Locale.ROOT, "%s  (%.2f)",
                String.valueOf(e.dietNow()).toLowerCase(Locale.ROOT), e.getDietBias())});
        righe.add(new String[]{"dieta alla nascita", String.format(Locale.ROOT, "%s  (gene %.2f)",
                String.valueOf(st.diet).toLowerCase(Locale.ROOT), st.dietBias)});
        righe.add(new String[]{"matura", e.isMature() ? "si'" : "no"});
        righe.add(new String[]{"attesa riproduzione", String.valueOf(e.getReproCooldown())});
        righe.add(new String[]{"salute", e.getHealt() + " / " + e.getMaxHealth()});
        righe.add(new String[]{"cibo", e.getFood() + " / " + entity.getMaxFood()});
        righe.add(new String[]{"acqua", e.getThirst() + " / " + entity.MAX_THIRST});
        righe.add(new String[]{"stamina", fmt(e.getStamina()) + " / " + fmt(st.maxStamina)});
        righe.add(new String[]{"dorme", e.isSleeping() ? "si'" : "no"});
        righe.add(new String[]{"mele mangiate", String.valueOf(e.getApplesEaten())});
        righe.add(new String[]{"cibo consumato", String.valueOf(e.getFoodConsumed())});
        righe.add(new String[]{"uccisioni", String.valueOf(e.getKills())});
        righe.add(new String[]{"ricompensa netta", fmt(e.getNetreward())});
        righe.add(new String[]{"fitness", fmt(e.getFitness())});
        righe.add(new String[]{"posizione (cella)", pos[0] + ", " + pos[1] + ", " + pos[2]});
        righe.add(new String[]{"posizione (esatta)",
                fmt(e.getExactX()) + ", " + fmt(e.getExactZ())});
        righe.add(new String[]{"direzione corpo", fmt(Math.toDegrees(e.getFacing())) + " gradi"});
        righe.add(new String[]{"direzione sguardo",
                fmt(Math.toDegrees(e.getGazeDirection())) + " gradi"});
        righe.add(new String[]{"vista adesso", fmt(e.sightNow()) + " celle"});
        righe.add(new String[]{"vista (gene)", fmt(st.sightDistance) + " celle"});
        righe.add(new String[]{"campo visivo", fmt(st.fieldOfView) + " gradi"});
        righe.add(new String[]{"visione notturna", fmt(st.nightVision)});
        righe.add(new String[]{"velocita' (gene)", fmt(st.speed)});
        righe.add(new String[]{"velocita' misurata", fmt(e.getWalkSpeed()) + " m/s"});
        righe.add(new String[]{"assorbimento cibo", fmt(st.foodAbsorption)});
        righe.add(new String[]{"attacco", fmt(st.attackPower)});
        righe.add(new String[]{"arriva in alto a", fmt(e.pickHeight()) + " m"});
        righe.add(new String[]{"portata braccia", fmt(e.reachNow()) + " m"});
        righe.add(new String[]{"lunghezza passo", fmt(e.stepLength()) + " m"});
        righe.add(new String[]{"ultima azione", e.process.isBlank() ? "-" : e.process});
        statistiche.set(righe);

        barre.set(new double[]{
                e.getHealt() / (double) Math.max(1, e.getMaxHealth()),
                e.getFood() / (double) Math.max(1, entity.getMaxFood()),
                e.getThirst() / (double) entity.MAX_THIRST,
                e.getStamina() / Math.max(1e-6, st.maxStamina)
        });

        geni.set(e);
        corpo.setText(descriviCorpo(e));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String descriviCorpo(entity e) {
        var t = e.getTraits();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "massa              %.2f kg%n", t.mass()));
        sb.append(String.format(Locale.ROOT, "altezza            %.2f m%n", t.height()));
        sb.append(String.format(Locale.ROOT, "portata            %.2f m%n", t.reach()));
        sb.append(String.format(Locale.ROOT, "lunghezza gamba    %.2f m%n", t.legLength()));
        sb.append(String.format(Locale.ROOT, "appoggi            %d%n", t.supportCount()));
        sb.append(String.format(Locale.ROOT, "estremita'         %d%n", t.extremityCount()));
        sb.append(String.format(Locale.ROOT, "locomozione        %s%n",
                t.crawls() ? "striscia" : "cammina"));
        var gait = e.getGait();
        sb.append(String.format(Locale.ROOT, "andatura           %s%n",
                gait == null ? "non misurata"
                        : String.format(Locale.ROOT, "%.2f Hz, ciclo %.2f s, %d oscillatori",
                                gait.frequency(), gait.cycleSeconds(),
                                gait.oscillators().size())));
        sb.append(String.format(Locale.ROOT, "velocita' misurata %.3f m/s%n", e.getWalkSpeed()));
        sb.append(System.lineSeparator());
        var automa = e.getBody();
        if (automa == null) {
            sb.append("Corpo non ancora costruito.");
            return sb.toString();
        }
        sb.append(String.format(Locale.ROOT,
                "Automa: %d segmenti, %d giunti, %d gradi di liberta', massa %.2f kg%n%n",
                automa.links().size(), automa.joints().size(), automa.dofCount(),
                automa.totalMass()));
        sb.append("Segmenti:").append(System.lineSeparator());
        for (var l : automa.links()) {
            sb.append("  ").append(l.name).append(System.lineSeparator());
        }
        return sb.toString();
    }

    /**
     * L'albero: i genitori verso l'alto (tutti gli antenati noti) e i figli verso il basso.
     *
     * Si costruisce una volta sola all'apertura, non a ogni rinfresco: un JTree ricostruito due
     * volte al secondo si richiude da solo a ogni giro, quindi non si riuscirebbe mai a
     * espandere un ramo. I parenti di un'entita' cambiano solo quando nasce un figlio, e per
     * vedere quello basta riaprire la scheda.
     */
    private void costruisciAlbero() {
        radice.removeAllChildren();
        Genealogy.Info mio = Genealogy.info(id);
        if (mio == null) {
            radice.add(new DefaultMutableTreeNode("Nessun dato genealogico per #" + id));
        } else {
            DefaultMutableTreeNode antenati = new DefaultMutableTreeNode("Antenati");
            aggiungiAntenati(antenati, mio, new HashSet<>(), 0);
            if (antenati.getChildCount() == 0) {
                antenati.add(new DefaultMutableTreeNode("fondatrice: nessun genitore"));
            }
            radice.add(antenati);

            DefaultMutableTreeNode io = new DefaultMutableTreeNode(new Parente(id, mio, "questa"));
            radice.add(io);

            DefaultMutableTreeNode discendenti = new DefaultMutableTreeNode("Discendenti");
            aggiungiDiscendenti(discendenti, id, new HashSet<>(), 0);
            if (discendenti.getChildCount() == 0) {
                discendenti.add(new DefaultMutableTreeNode("nessun figlio"));
            }
            radice.add(discendenti);
        }
        modelloAlbero.reload();
        for (int i = 0; i < albero.getRowCount(); i++) {
            albero.expandRow(i);
        }
    }

    /** Profondita' massima: oltre, un albero di 15 generazioni diventa illeggibile. */
    private static final int PROFONDITA = 6;

    private static void aggiungiAntenati(DefaultMutableTreeNode nodo, Genealogy.Info info,
                                         Set<Integer> visti, int profondita) {
        if (profondita >= PROFONDITA) {
            return;
        }
        for (int p : new int[]{info.parentA(), info.parentB()}) {
            if (p < 0 || !visti.add(p)) {
                continue;
            }
            Genealogy.Info gi = Genealogy.info(p);
            DefaultMutableTreeNode figlio = new DefaultMutableTreeNode(
                    new Parente(p, gi, profondita == 0 ? "genitore" : "antenato"));
            nodo.add(figlio);
            if (gi != null) {
                aggiungiAntenati(figlio, gi, visti, profondita + 1);
            }
        }
    }

    private static void aggiungiDiscendenti(DefaultMutableTreeNode nodo, int id,
                                            Set<Integer> visti, int profondita) {
        if (profondita >= PROFONDITA) {
            return;
        }
        for (int f : Genealogy.childrenOf(id)) {
            if (!visti.add(f)) {
                continue;
            }
            Genealogy.Info gi = Genealogy.info(f);
            DefaultMutableTreeNode figlio = new DefaultMutableTreeNode(
                    new Parente(f, gi, profondita == 0 ? "figlia" : "discendente"));
            nodo.add(figlio);
            aggiungiDiscendenti(figlio, f, visti, profondita + 1);
        }
    }

    /** Un parente nell'albero: il testo che si legge nel nodo e l'id su cui si clicca. */
    private record Parente(int id, Genealogy.Info info, String ruolo) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("#" + id + "  " + ruolo);
            if (info != null) {
                sb.append("  -  gen ").append(info.generation())
                  .append(", nata al tick ").append(info.birthTick());
                sb.append(info.alive() ? ", viva"
                        : ", morta al tick " + info.deathTick() + " (" + info.deathCause() + ")");
            }
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------ modelli e barre

    /** Tabella a due colonne: nome del dato e valore. */
    private static final class ModelloCoppie extends AbstractTableModel {
        private List<String[]> righe = List.of();

        void set(List<String[]> righe) {
            this.righe = righe;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return righe.size(); }
        @Override public int getColumnCount() { return 2; }
        @Override public String getColumnName(int c) { return c == 0 ? "dato" : "valore"; }
        @Override public Object getValueAt(int r, int c) { return righe.get(r)[c]; }
    }

    /**
     * I geni: valore espresso, i due alleli e l'intervallo in cui il gene puo' stare.
     *
     * L'intervallo c'e' perche' senza di quello un numero non dice niente: "vista 7.4" si legge
     * solo sapendo che quel gene va da 3 a 12. La colonna "effetti" dice su quali statistiche
     * quel gene pesa, cosi' si capisce perche' due entita' con la stessa vista corrono diverso.
     */
    private static final class ModelloGeni extends AbstractTableModel {
        private static final String[] COLONNE =
                {"gene", "espresso", "allele A", "allele B", "min", "max", "effetti"};
        private List<Object[]> righe = List.of();

        void set(entity e) {
            GenomeSchema schema = entity.getSchema();
            List<Object[]> nuove = new ArrayList<>();
            for (GeneDefinition g : schema.allGenes()) {
                var all = e.getGenome().get(g.id);
                if (all == null) {
                    continue;
                }
                nuove.add(new Object[]{
                        g.id,
                        PhenotypeResolver.resolve(g.id, e.getGenome(), schema),
                        all.alleleA,
                        all.alleleB,
                        g.realMin,
                        g.realMax,
                        String.join(", ", g.affects)
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
            return c >= 1 && c <= 5 ? Double.class : String.class;
        }
    }

    /**
     * Le quattro riserve vitali come barre.
     *
     * Qui la barra serve: salute, cibo, acqua e stamina sono frazioni di un massimo, e la cosa
     * che si vuole sapere guardando una scheda e' "quanto e' pieno", non il numero esatto - che
     * c'e' comunque, scritto dentro la barra.
     */
    private final class Barre extends JPanel {
        private static final String[] NOMI = {"salute", "cibo", "acqua", "stamina"};
        private static final Color[] COLORI = {
                new Color(0xB02A1F), new Color(0xEB6834), new Color(0x2A78D6), new Color(0x7B5EA7)
        };
        private double[] valori;

        Barre() {
            setBackground(Tema.SFONDO);
            setPreferredSize(new Dimension(10, 4 * 24 + 10));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        }

        void set(double[] v) {
            this.valori = v;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (valori == null) {
                return;
            }
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(Tema.NORMALE);
            int x = 74;
            // a destra resta lo spazio per la percentuale scritta accanto alla barra
            int w = Math.max(60, getWidth() - x - 60);
            for (int i = 0; i < NOMI.length; i++) {
                int y = 8 + i * 24;
                g.setColor(Tema.TESTO);
                g.drawString(NOMI[i], 10, y + 12);
                g.setColor(Tema.GRIGLIA);
                g.fillRoundRect(x, y, w, 15, 4, 4);
                double f = Math.max(0, Math.min(1, valori[i]));
                g.setColor(COLORI[i]);
                g.fillRoundRect(x, y, Math.max(4, (int) (w * f)), 15, 4, 4);
                g.setColor(Tema.TESTO);
                g.drawString(String.format(Locale.ROOT, "%.0f%%", f * 100), x + w + 6, y + 12);
            }
        }
    }
}
