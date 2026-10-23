package src.gui;

import src.History;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * Un grafico a linee (o ad aree impilate) sui campioni di {@link History}.
 *
 * UNA SOLA SCALA VERTICALE. Tutte le serie di un grafico stanno sullo stesso asse: niente
 * secondo asse a destra. Due assi verticali permettono di far incrociare due curve dove si
 * vuole, semplicemente scegliendo le due scale, quindi la forma del grafico diventa una
 * decisione di chi lo disegna invece che un fatto sui dati. Se due grandezze hanno ordini di
 * grandezza diversi vanno in due grafici, come qui popolazione e morti.
 *
 * I DATI SI RIDUCONO, NON SI BUTTANO. Quando i campioni sono piu' dei pixel disponibili si
 * prende un campione ogni n: la curva resta quella e il disegno non costa piu' del necessario.
 */
class GraficoStorico extends JPanel {

    /** Una serie: come si chiama, di che colore e', e come si ricava da un campione. */
    record Serie(String nome, Color colore, ToDoubleFunction<History.Sample> valore) {
    }

    private static final int MARGINE_SX = 52;
    private static final int MARGINE_DX = 14;
    private static final int MARGINE_SU = 30;
    private static final int MARGINE_GIU = 34;

    private final String titolo;
    /** true = aree impilate (le serie si sommano), false = linee indipendenti. */
    private final boolean impilato;
    private final List<Serie> serie;

    private List<History.Sample> campioni = List.of();
    /** Indice del campione sotto il mouse, -1 se il mouse e' fuori. */
    private int evidenziato = -1;

    GraficoStorico(String titolo, boolean impilato, Serie... serie) {
        this.titolo = titolo;
        this.impilato = impilato;
        this.serie = List.of(serie);
        setBackground(Tema.PANNELLO);
        setPreferredSize(new Dimension(640, 320));
        setFont(Tema.NORMALE);

        // Il grafico e' interattivo per difetto: passare col mouse mostra i valori esatti di quel
        // tick, cosi' non serve indovinare l'altezza di un punto leggendo l'asse.
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = indiceVicino(e.getX());
                if (i != evidenziato) {
                    evidenziato = i;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (evidenziato != -1) {
                    evidenziato = -1;
                    repaint();
                }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    /** Rilegge la storia e ridisegna: la chiama il timer della finestra. */
    void aggiorna() {
        campioni = ridotti(History.snapshot(), Math.max(40, larghezzaGrafico()));
        repaint();
    }

    private int larghezzaGrafico() {
        return Math.max(1, getWidth() - MARGINE_SX - MARGINE_DX);
    }

    /** Un campione ogni n, tenendo sempre il primo e l'ultimo. */
    private static List<History.Sample> ridotti(List<History.Sample> tutti, int quanti) {
        if (tutti.size() <= quanti) {
            return tutti;
        }
        int passo = (int) Math.ceil(tutti.size() / (double) quanti);
        List<History.Sample> out = new ArrayList<>(quanti + 1);
        for (int i = 0; i < tutti.size(); i += passo) {
            out.add(tutti.get(i));
        }
        History.Sample ultimo = tutti.get(tutti.size() - 1);
        if (out.get(out.size() - 1) != ultimo) {
            out.add(ultimo);
        }
        return out;
    }

    private int indiceVicino(int px) {
        if (campioni.isEmpty()) {
            return -1;
        }
        int w = larghezzaGrafico();
        double f = (px - MARGINE_SX) / (double) w;
        if (f < -0.02 || f > 1.02) {
            return -1;
        }
        int i = (int) Math.round(f * (campioni.size() - 1));
        return Math.max(0, Math.min(campioni.size() - 1, i));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int x0 = MARGINE_SX;
        int y0 = MARGINE_SU;
        int w = larghezzaGrafico();
        int h = Math.max(1, getHeight() - MARGINE_SU - MARGINE_GIU);

        g.setFont(Tema.TITOLO);
        g.setColor(Tema.TESTO);
        g.drawString(titolo, MARGINE_SX, 18);
        g.setFont(Tema.NORMALE);

        if (campioni.size() < 2) {
            g.setColor(Tema.TESTO_DEBOLE);
            g.drawString("Nessun dato: avvia la simulazione (comando newstart).", x0, y0 + h / 2);
            return;
        }

        double max = massimo();
        double passoY = passoAsse(max);
        double altoY = Math.max(passoY, Math.ceil(max / passoY) * passoY);

        // griglia e valori dell'asse verticale: sottili e grigi, stanno dietro ai dati
        g.setStroke(new BasicStroke(1f));
        for (double v = 0; v <= altoY + 1e-9; v += passoY) {
            int y = (int) Math.round(y0 + h - h * v / altoY);
            g.setColor(Tema.GRIGLIA);
            g.drawLine(x0, y, x0 + w, y);
            g.setColor(Tema.TESTO_DEBOLE);
            String et = formato(v);
            g.drawString(et, x0 - 8 - g.getFontMetrics().stringWidth(et), y + 4);
        }

        // asse dei tick in basso: solo tre etichette (primo, mezzo, ultimo)
        g.setColor(Tema.ASSE);
        g.drawLine(x0, y0 + h, x0 + w, y0 + h);
        g.setColor(Tema.TESTO_DEBOLE);
        for (int k = 0; k <= 2; k++) {
            int i = k * (campioni.size() - 1) / 2;
            int x = x0 + (int) Math.round(w * i / (double) (campioni.size() - 1));
            String et = "tick " + campioni.get(i).tick;
            int tw = g.getFontMetrics().stringWidth(et);
            int tx = Math.max(x0, Math.min(x0 + w - tw, x - tw / 2));
            g.drawString(et, tx, y0 + h + 18);
        }

        if (impilato) {
            disegnaImpilato(g, x0, y0, w, h, altoY);
        } else {
            disegnaLinee(g, x0, y0, w, h, altoY);
        }

        legenda(g, x0, y0 + h + 28);

        if (evidenziato >= 0 && evidenziato < campioni.size()) {
            evidenzia(g, x0, y0, w, h, altoY);
        }
    }

    private double massimo() {
        double max = 1;
        for (History.Sample s : campioni) {
            if (impilato) {
                double somma = 0;
                for (Serie se : serie) {
                    somma += se.valore().applyAsDouble(s);
                }
                max = Math.max(max, somma);
            } else {
                for (Serie se : serie) {
                    max = Math.max(max, se.valore().applyAsDouble(s));
                }
            }
        }
        return max;
    }

    /** Un passo dell'asse "tondo" (1, 2, 5, 10, 20, 50...) con circa 5 tacche. */
    private static double passoAsse(double max) {
        double grezzo = max / 5.0;
        double ordine = Math.pow(10, Math.floor(Math.log10(Math.max(1e-9, grezzo))));
        double n = grezzo / ordine;
        double scelto = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
        return Math.max(1, scelto * ordine);
    }

    private static String formato(double v) {
        return v >= 1000 ? String.format(Locale.ROOT, "%.0fk", v / 1000) : String.format(Locale.ROOT, "%.0f", v);
    }

    private int px(int i, int x0, int w) {
        return x0 + (int) Math.round(w * i / (double) (campioni.size() - 1));
    }

    private int py(double v, int y0, int h, double altoY) {
        return y0 + h - (int) Math.round(h * v / altoY);
    }

    private void disegnaLinee(Graphics2D g, int x0, int y0, int w, int h, double altoY) {
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (Serie se : serie) {
            g.setColor(se.colore());
            int[] xs = new int[campioni.size()];
            int[] ys = new int[campioni.size()];
            for (int i = 0; i < campioni.size(); i++) {
                xs[i] = px(i, x0, w);
                ys[i] = py(se.valore().applyAsDouble(campioni.get(i)), y0, h, altoY);
            }
            g.drawPolyline(xs, ys, xs.length);
        }
    }

    /**
     * Aree impilate: ogni serie si somma a quelle sotto, quindi il bordo superiore e' il totale.
     * Tra due aree resta una riga del colore del pannello, cosi' il confine si vede anche quando
     * una fascia e' alta due pixel.
     */
    private void disegnaImpilato(Graphics2D g, int x0, int y0, int w, int h, double altoY) {
        int n = campioni.size();
        double[] base = new double[n];
        for (Serie se : serie) {
            int[] xs = new int[n * 2];
            int[] ys = new int[n * 2];
            for (int i = 0; i < n; i++) {
                double sotto = base[i];
                double sopra = sotto + se.valore().applyAsDouble(campioni.get(i));
                xs[i] = px(i, x0, w);
                ys[i] = py(sopra, y0, h, altoY);
                xs[2 * n - 1 - i] = xs[i];
                ys[2 * n - 1 - i] = py(sotto, y0, h, altoY);
                base[i] = sopra;
            }
            g.setColor(se.colore());
            g.fillPolygon(xs, ys, xs.length);
            g.setColor(Tema.PANNELLO);
            g.setStroke(new BasicStroke(2f));
            g.drawPolyline(xs, ys, n);
        }
    }

    /**
     * La legenda c'e' sempre quando le serie sono due o piu': l'identita' di una curva non deve
     * dipendere solo dal colore.
     */
    private void legenda(Graphics2D g, int x, int y) {
        if (serie.size() < 2) {
            return;
        }
        g.setFont(Tema.NORMALE);
        int cx = x;
        for (Serie se : serie) {
            g.setColor(se.colore());
            g.fillRect(cx, y - 8, 10, 10);
            g.setColor(Tema.TESTO);
            g.drawString(se.nome(), cx + 15, y + 1);
            cx += 15 + g.getFontMetrics().stringWidth(se.nome()) + 18;
        }
    }

    /** Mirino verticale e riquadro con i valori esatti del campione sotto il mouse. */
    private void evidenzia(Graphics2D g, int x0, int y0, int w, int h, double altoY) {
        History.Sample s = campioni.get(evidenziato);
        int x = px(evidenziato, x0, w);
        g.setColor(Tema.ASSE);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
                new float[]{3f, 3f}, 0f));
        g.drawLine(x, y0, x, y0 + h);
        g.setStroke(new BasicStroke(1f));

        List<String> righe = new ArrayList<>();
        righe.add("tick " + s.tick);
        double cumulo = 0;
        for (Serie se : serie) {
            double v = se.valore().applyAsDouble(s);
            cumulo += v;
            righe.add(se.nome() + ": " + formato(v));
            int y = py(impilato ? cumulo : v, y0, h, altoY);
            g.setColor(Tema.PANNELLO);
            g.fillOval(x - 5, y - 5, 10, 10);
            g.setColor(se.colore());
            g.fillOval(x - 3, y - 3, 6, 6);
        }

        int larg = 0;
        for (String r : righe) {
            larg = Math.max(larg, g.getFontMetrics().stringWidth(r));
        }
        int bw = larg + 16;
        int bh = righe.size() * 15 + 10;
        int bx = x + 12 + bw > x0 + w ? x - 12 - bw : x + 12;
        int by = Math.min(y0 + 6, y0 + h - bh);
        g.setColor(new Color(255, 255, 255, 235));
        g.fillRoundRect(bx, by, bw, bh, 6, 6);
        g.setColor(Tema.GRIGLIA);
        g.drawRoundRect(bx, by, bw, bh, 6, 6);
        g.setColor(Tema.TESTO);
        for (int i = 0; i < righe.size(); i++) {
            g.setFont(i == 0 ? Tema.GRASSETTO : Tema.NORMALE);
            g.drawString(righe.get(i), bx + 8, by + 18 + i * 15);
        }
    }
}
