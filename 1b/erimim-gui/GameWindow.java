import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import src.*;

/**
 * Motore grafico standalone 3D (isometrico, stile voxel) per il progetto "erimim".
 * Non modifica il codice originale in ../erimim: importa il package src (world, entity)
 * e ne visualizza lo stato in tempo reale, con asset diversi per ogni tipo di marker:
 *  - cubi voxel per il terreno
 *  - sfere rosse per le mele
 *  - omini stilizzati per le entita'
 *
 * In fondo al pannello di log e' presente una console testuale collegata alla stessa
 * logica di comandi del "console()" originale (main.java): layer, start, stop,
 * TotStart, TotStop, mela, setFood, print.
 */
public class GameWindow extends JFrame {

    private final world planet = new world();
    private final ArrayList<entity> entityList = new ArrayList<>();
    private int cycle = 0;
    private final AtomicInteger ground = new AtomicInteger();
    private int terrainHeight; // altezza fissa del blocco di terreno (numero di livelli solidi)
    private final AtomicBoolean cycleRunning = new AtomicBoolean(false);
    private final AtomicBoolean totCycle = new AtomicBoolean(false);

    private IsoWorldPanel worldPanel;
    private JTextArea logArea;
    private JTextField consoleField;
    private JLabel statusLabel;

    // --- Ispezione entita': click sullo sprite -> stats + storico azioni ---
    private final Map<Integer, EntityTracker> trackers = new HashMap<>();
    private final List<int[]> entityHitRegions = new ArrayList<>(); // {id, screenX, screenY}
    private Integer selectedEntityId = null;
    private EntityInspectorDialog inspectorDialog;
    private static final int HIT_RADIUS = 16;

    public GameWindow() {
        super("Erimim - Motore Grafico 3D");
        setupWorld();
        buildUI();
        redirectConsole();

        Timer repaintTimer = new Timer(300, e -> {
            updateTrackers();
            worldPanel.repaint();
            statusLabel.setText("Livello mela: " + ground.get()
                    + "   Ciclo: " + cycle
                    + "   Entita': " + entityList.size()
                    + "   Stato: " + (cycleRunning.get() ? "IN ESECUZIONE" : "FERMO")
                    + (selectedEntityId != null ? "   Selezionata: #" + selectedEntityId : ""));
        });
        repaintTimer.start();
    }

    /** Campiona ogni entita' viva (e quelle appena decedute) e aggiorna il relativo tracker. */
    private void updateTrackers() {
        for (entity e : entityList) {
            int id = e.getId();
            int[] pos;
            try {
                pos = e.getPos();
            } catch (Exception ex) {
                continue;
            }
            EntityTracker t = trackers.computeIfAbsent(id, EntityTracker::new);
            boolean changed = t.update(pos, e.getSex(), e.getNetreward(), e.life.get(), e.getFood(), e.getHealt());
            if (changed && selectedEntityId != null && selectedEntityId == id
                    && inspectorDialog != null && inspectorDialog.isVisible()) {
                inspectorDialog.refresh(t);
            }
        }
    }

    private void selectEntity(int id) {
        selectedEntityId = id;
        EntityTracker t = trackers.computeIfAbsent(id, EntityTracker::new);
        if (inspectorDialog == null) {
            inspectorDialog = new EntityInspectorDialog(this);
        }
        inspectorDialog.refresh(t);
        inspectorDialog.setVisible(true);
        worldPanel.repaint();
    }

    private void setupWorld() {
        planet.world_setup();
        ground.set(planet.ground_search());
        terrainHeight = ground.get(); // livelli 0..terrainHeight-1 sono terra solida

        int startEntity = 10;
        for (int i = 0; i < startEntity; i++) {
            entity e = new entity(i, cycle,
                    (int) (Math.random() * (planet.getDim() - 1)) + 1,
                    ground.get(),
                    (int) (Math.random() * (planet.getDim() - 1)) + 1);
            entityList.add(e);
            Thread t = new Thread(() -> {
                e.setProcessId(Thread.currentThread().getName());
                e.GoLife();
            });
            t.setDaemon(true);
            t.start();
        }
        planet.add(5, ground.get(), 6, "M");
    }

    private void buildUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1150, 720);
        setLocationRelativeTo(null);

        worldPanel = new IsoWorldPanel();
        worldPanel.setPreferredSize(new Dimension(650, 620));
        worldPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Integer hit = findEntityAt(e.getX(), e.getY());
                if (hit != null) {
                    selectEntity(hit);
                }
            }
        });

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);

        consoleField = new JTextField();
        consoleField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        consoleField.setToolTipText("Comandi: layer N | start | stop | TotStart | TotStop | mela x z | setFood id valore | print");
        consoleField.addActionListener(e -> {
            String cmd = consoleField.getText();
            consoleField.setText("");
            if (cmd != null && !cmd.isBlank()) {
                System.out.println("> " + cmd);
                processConsoleCommand(cmd);
            }
        });

        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        inputRow.add(new JLabel("Console:"), BorderLayout.WEST);
        inputRow.add(consoleField, BorderLayout.CENTER);
        inputRow.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.add(logScroll, BorderLayout.CENTER);
        logPanel.add(inputRow, BorderLayout.SOUTH);
        logPanel.setPreferredSize(new Dimension(450, 620));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton startBtn = new JButton("Start ciclo");
        JButton stopBtn = new JButton("Stop ciclo");
        JButton layerUpBtn = new JButton("Livello mela +");
        JButton layerDownBtn = new JButton("Livello mela -");
        JButton addMelaBtn = new JButton("Aggiungi mela random");

        startBtn.addActionListener(e -> startCycle());
        stopBtn.addActionListener(e -> stopCycle());
        layerUpBtn.addActionListener(e -> {
            int g = ground.get();
            if (g < planet.getDim() - 1) ground.set(g + 1);
        });
        layerDownBtn.addActionListener(e -> {
            int g = ground.get();
            if (g > 0) ground.set(g - 1);
        });
        addMelaBtn.addActionListener(e -> {
            int x = (int) (Math.random() * planet.getDim());
            int z = (int) (Math.random() * planet.getDim());
            planet.add(x, ground.get(), z, "M");
            System.out.println("Aggiunta mela in " + x + "," + ground.get() + "," + z);
        });

        controls.add(startBtn);
        controls.add(stopBtn);
        controls.add(layerUpBtn);
        controls.add(layerDownBtn);
        controls.add(addMelaBtn);

        statusLabel = new JLabel("Livello mela: 0   Ciclo: 0");
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(statusLabel, BorderLayout.WEST);

        JPanel centerSplit = new JPanel(new BorderLayout());
        centerSplit.add(worldPanel, BorderLayout.CENTER);
        centerSplit.add(logPanel, BorderLayout.EAST);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(controls, BorderLayout.NORTH);
        getContentPane().add(centerSplit, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);
    }

    /**
     * Stessa logica di comando del "console()" originale in main.java, riadattata
     * per scrivere sulla console reindirizzata nella finestra invece che su System.in/out.
     */
    private void processConsoleCommand(String cmd) {
        String[] str = cmd.trim().split("\\s+");
        try {
            switch (str[0]) {
                case "layer":
                    int l = Integer.parseInt(str[1]);
                    if (l >= 0 && l < planet.getDim()) {
                        ground.set(l);
                        System.out.println("Layer impostato a: " + l);
                    } else {
                        System.out.println("Layer fuori range (0-" + (planet.getDim() - 1) + ")");
                    }
                    break;
                case "start":
                    if (!totCycle.get()) {
                        startCycle();
                    } else {
                        cycleRunning.set(true);
                        System.out.println("Ciclo ripreso");
                    }
                    break;
                case "stop":
                    cycleRunning.set(false);
                    System.out.println("Ciclo in pausa");
                    break;
                case "TotStart":
                    startCycle();
                    break;
                case "TotStop":
                    stopCycle();
                    System.out.println("Ciclo fermato completamente");
                    break;
                case "mela":
                    int mx = Integer.parseInt(str[1]);
                    int mz = Integer.parseInt(str[2]);
                    if (mx >= 0 && mx <= planet.getDim() && mz >= 0 && mz <= planet.getDim()) {
                        planet.add(mx, ground.get(), mz, "M");
                        System.out.println("Mela aggiunta in " + mx + "," + ground.get() + "," + mz);
                    }
                    break;
                case "setFood":
                    int id = Integer.parseInt(str[1].trim());
                    int val = Integer.parseInt(str[2].trim());
                    if (val > 0 && id >= 0 && id < entityList.size()) {
                        entityList.get(id).setFood(val);
                        System.out.println("Entity: " + entityList.get(id));
                    }
                    break;
                case "print":
                    printPlanetText(ground.get());
                    break;
                default:
                    System.out.println("Parametro sconosciuto: " + str[0]);
                    break;
            }
        } catch (Exception e) {
            System.out.println("Errore comando '" + cmd + "': " + e.getMessage());
        }
    }

    /** Ricostruisce testualmente la matrice del layer, come il vecchio planetPrint() di main.java. */
    private void printPlanetText(int gr) {
        try {
            int dim = planet.getDim();
            StringBuilder sb = new StringBuilder();
            sb.append("\t|\t");
            for (int j = 0; j < dim; j++) sb.append(j).append("\t");
            sb.append("\n");
            sb.append("--------".repeat(dim + 1)).append("\n");
            for (int i = 0; i < dim; i++) {
                sb.append(i).append("\t|\t");
                for (int j = 0; j < dim; j++) {
                    String sym = world.getSymbol(i, gr, j);
                    String tag = "";
                    for (entity e : entityList) {
                        int[] p = e.getPos();
                        if (p[0] == i && p[1] == gr && p[2] == j) tag = " E" + e.getId();
                    }
                    sb.append(sym).append(tag).append("\t");
                }
                sb.append("|\n");
            }
            System.out.print(sb);
        } catch (Exception e) {
            System.out.println("Matrice vuota!! " + e.getMessage());
        }
    }

    private void redirectConsole() {
        OutputStream out = new OutputStream() {
            private final StringBuilder buffer = new StringBuilder();

            @Override
            public synchronized void write(int b) {
                buffer.append((char) b);
                if (b == '\n') {
                    String text = buffer.toString();
                    buffer.setLength(0);
                    SwingUtilities.invokeLater(() -> {
                        logArea.append(text);
                        if (logArea.getDocument().getLength() > 200000) {
                            try {
                                logArea.getDocument().remove(0, 50000);
                            } catch (Exception ex) {
                                // ignore
                            }
                        }
                        logArea.setCaretPosition(logArea.getDocument().getLength());
                    });
                }
            }
        };
        PrintStream ps = new PrintStream(out, true);
        System.setOut(ps);
        System.setErr(ps);
    }

    private void startCycle() {
        if (cycleRunning.get()) return;
        cycleRunning.set(true);
        totCycle.set(true);
        Thread cycleThread = new Thread(() -> {
            int ore = 0;
            System.out.println("Cycle start");
            try {
                while (totCycle.get()) {
                    Thread.sleep(1000);
                    while (cycleRunning.get()) {
                        Thread.sleep(2000);
                        System.out.println("Cycle: " + cycle + " - ore: " + ore
                                + " - entita': " + entityList.size());
                        ore++;
                        if (ore == 24) {
                            ore = 0;
                            cycle++;
                            System.out.println("Nuovo ciclo");
                        }
                        if (!totCycle.get()) break;
                    }
                }
                System.out.println("Cycle stopped");
            } catch (InterruptedException e) {
                System.out.println("Cycle thread interrotto");
            }
        });
        cycleThread.setDaemon(true);
        cycleThread.start();
    }

    private void stopCycle() {
        cycleRunning.set(false);
        totCycle.set(false);
    }

    /** Trova l'entita' il cui sprite e' piu' vicino al punto cliccato, entro HIT_RADIUS px. */
    private Integer findEntityAt(int x, int y) {
        Integer bestId = null;
        double bestDist = HIT_RADIUS;
        synchronized (entityHitRegions) {
            for (int[] hr : entityHitRegions) {
                double dist = Point2D.distance(x, y, hr[1], hr[2]);
                if (dist <= bestDist) {
                    bestDist = dist;
                    bestId = hr[0];
                }
            }
        }
        return bestId;
    }

    /**
     * Pannello di rendering isometrico voxel-style.
     * Asset diversi per marker:
     *  - blocco terreno: cubo con faccia superiore erbosa e lati di terra
     *  - mela: sfera rossa con riflesso
     *  - entita': omino stilizzato (testa + corpo) con ombra a terra
     */
    private class IsoWorldPanel extends JPanel {

        private static final int TILE_W = 46;   // larghezza diamante (footprint)
        private static final int TILE_H = 23;   // altezza diamante
        private static final int BLOCK_H = 20;  // estrusione verticale per livello

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            GradientPaint sky = new GradientPaint(0, 0, new Color(135, 206, 250), 0, h, new Color(225, 245, 255));
            g2.setPaint(sky);
            g2.fillRect(0, 0, w, h);

            int dim = planet.getDim();
            int centerX = w / 2;
            int centerY = h / 3;

            Map<Integer, List<int[]>> applesByColumn = new HashMap<>();
            for (int x = 0; x < dim; x++) {
                for (int z = 0; z < dim; z++) {
                    for (int y = 0; y < dim; y++) {
                        String s;
                        try {
                            s = world.getSymbol(x, y, z);
                        } catch (Exception ex) {
                            s = "";
                        }
                        if ("M".equals(s)) {
                            applesByColumn.computeIfAbsent(x * dim + z, k -> new ArrayList<>())
                                    .add(new int[]{y});
                        }
                    }
                }
            }
            Map<Integer, List<entity>> entitiesByColumn = new HashMap<>();
            for (entity e : entityList) {
                int[] pos;
                try {
                    pos = e.getPos();
                } catch (Exception ex) {
                    continue;
                }
                entitiesByColumn.computeIfAbsent(pos[0] * dim + pos[2], k -> new ArrayList<>()).add(e);
            }

            List<int[]> newHitRegions = new ArrayList<>();

            for (int diag = 0; diag <= 2 * (dim - 1); diag++) {
                int iStart = Math.max(0, diag - dim + 1);
                int iEnd = Math.min(dim - 1, diag);
                for (int i = iStart; i <= iEnd; i++) {
                    int j = diag - i;

                    Point iso = toScreen(i, terrainHeight, j, centerX, centerY);
                    drawTerrainColumn(g2, iso.x, iso.y, terrainHeight * BLOCK_H);

                    int key = i * dim + j;
                    List<int[]> apples = applesByColumn.get(key);
                    if (apples != null) {
                        for (int[] a : apples) {
                            Point p = toScreen(i, a[0], j, centerX, centerY);
                            drawApple(g2, p.x, p.y + TILE_H / 2);
                        }
                    }
                    List<entity> ents = entitiesByColumn.get(key);
                    if (ents != null) {
                        for (entity e : ents) {
                            int[] pos = e.getPos();
                            Point p = toScreen(pos[0], pos[1], pos[2], centerX, centerY);
                            int cx = p.x;
                            int cy = p.y + TILE_H / 2;
                            boolean selected = selectedEntityId != null && selectedEntityId == e.getId();
                            drawEntity(g2, cx, cy, e.getId(), selected);
                            newHitRegions.add(new int[]{e.getId(), cx, cy});
                        }
                    }
                }
            }

            synchronized (entityHitRegions) {
                entityHitRegions.clear();
                entityHitRegions.addAll(newHitRegions);
            }
        }

        private Point toScreen(int i, int y, int j, int centerX, int centerY) {
            int screenX = centerX + (i - j) * (TILE_W / 2);
            int screenY = centerY + (i + j) * (TILE_H / 2) - y * BLOCK_H;
            return new Point(screenX, screenY);
        }

        private void drawTerrainColumn(Graphics2D g2, int px, int py, int height) {
            Color grassTop = new Color(95, 165, 70);
            Color dirtLeft = new Color(101, 67, 33);
            Color dirtRight = new Color(126, 84, 42);

            Polygon left = new Polygon();
            left.addPoint(px - TILE_W / 2, py + TILE_H / 2);
            left.addPoint(px, py + TILE_H);
            left.addPoint(px, py + TILE_H + height);
            left.addPoint(px - TILE_W / 2, py + TILE_H / 2 + height);
            g2.setColor(dirtLeft);
            g2.fillPolygon(left);

            Polygon right = new Polygon();
            right.addPoint(px, py + TILE_H);
            right.addPoint(px + TILE_W / 2, py + TILE_H / 2);
            right.addPoint(px + TILE_W / 2, py + TILE_H / 2 + height);
            right.addPoint(px, py + TILE_H + height);
            g2.setColor(dirtRight);
            g2.fillPolygon(right);

            Polygon top = new Polygon();
            top.addPoint(px, py);
            top.addPoint(px + TILE_W / 2, py + TILE_H / 2);
            top.addPoint(px, py + TILE_H);
            top.addPoint(px - TILE_W / 2, py + TILE_H / 2);
            g2.setColor(grassTop);
            g2.fillPolygon(top);
            g2.setColor(new Color(0, 0, 0, 40));
            g2.drawPolygon(top);
            g2.drawPolygon(left);
            g2.drawPolygon(right);
        }

        private void drawApple(Graphics2D g2, int cx, int cy) {
            int r = 9;
            int topY = cy - r - 14;
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillOval(cx - r / 2, cy - 3, r, r / 2);
            RadialGradientPaint grad = new RadialGradientPaint(
                    new Point2D.Float(cx - 3, topY - 3 + r), r + 2,
                    new float[]{0f, 1f},
                    new Color[]{new Color(255, 120, 110), new Color(178, 24, 24)});
            g2.setPaint(grad);
            g2.fillOval(cx - r, topY, r * 2, r * 2);
            g2.setColor(new Color(60, 100, 30));
            g2.fillRect(cx - 1, topY - 4, 2, 5);
        }

        private void drawEntity(Graphics2D g2, int cx, int cy, int id, boolean selected) {
            Color[] palette = {
                    new Color(30, 110, 210), new Color(210, 140, 30), new Color(30, 160, 120),
                    new Color(160, 60, 180), new Color(200, 60, 60), new Color(60, 150, 200)
            };
            Color body = palette[Math.floorMod(id, palette.length)];

            int bodyH = 22;
            int headR = 7;
            int baseY = cy;

            if (selected) {
                g2.setColor(new Color(255, 215, 0));
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawOval(cx - 13, baseY - bodyH - headR * 2 - 2, 26, bodyH + headR * 2 + 8);
                g2.setStroke(new BasicStroke(1f));
            }

            g2.setColor(new Color(0, 0, 0, 70));
            g2.fillOval(cx - 9, baseY - 3, 18, 6);

            g2.setColor(body);
            g2.fillRoundRect(cx - 5, baseY - bodyH, 10, bodyH - headR + 2, 5, 5);

            g2.setColor(new Color(255, 224, 189));
            g2.fillOval(cx - headR, baseY - bodyH - headR * 2 + 4, headR * 2, headR * 2);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            String label = String.valueOf(id);
            int lw = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, cx - lw / 2, baseY + 12);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameWindow win = new GameWindow();
            win.setVisible(true);
        });
    }
}