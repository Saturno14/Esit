package src.gui;

import src.Entity_manager;
import src.History;
import src.Simulation;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;

/**
 * La crescita della popolazione: la curva dei vivi, e accanto le nascite cumulative.
 *
 * VIVI E NASCITE SONO DUE COSE DIVERSE, quindi sono due curve e non una. I vivi salgono e
 * scendono (le entita' muoiono); le nascite non scendono mai perche' sono un totale. Tenerle
 * insieme fa vedere la cosa che interessa: quando la popolazione si appiattisce mentre le
 * nascite continuano a salire, non e' che non nasce nessuno - e' che muoiono alla stessa
 * velocita' con cui nascono.
 */
class PannelloPopolazione extends JPanel {

    private final GraficoStorico grafico = new GraficoStorico(
            "Popolazione", false,
            new GraficoStorico.Serie("vive adesso", Tema.POPOLAZIONE, s -> s.popolazione),
            new GraficoStorico.Serie("nascite (totale)", Tema.NASCITE, s -> s.nascite),
            new GraficoStorico.Serie("morti (totale)", Tema.MORTI, s -> s.mortiTotali()));

    private final Targhetta tVive = new Targhetta("Vive adesso", Tema.POPOLAZIONE);
    private final Targhetta tPicco = new Targhetta("Picco", Tema.POPOLAZIONE);
    private final Targhetta tNascite = new Targhetta("Nascite", Tema.NASCITE);
    private final Targhetta tMorti = new Targhetta("Morti", Tema.MORTI);
    private final Targhetta tMaiVissute = new Targhetta("Mai vissute", Tema.TESTO_DEBOLE);
    private final Targhetta tGenerazione = new Targhetta("Generazione max", Tema.NASCITE);
    private final Targhetta tSessi = new Targhetta("Maschi / femmine", Tema.POPOLAZIONE);
    /**
     * Quante coppie possono ancora fare figli: e' il minimo tra maschi e femmine in eta' da
     * riproduzione. A zero la popolazione e' gia' finita anche se le entita' sono vive e il
     * bosco e' pieno di mele - e' successo davvero (sessione 19: due superstiti, tutti e due
     * maschi, 2000 tick senza una nascita con 400 mele a terra).
     */
    private final Targhetta tCoppie = new Targhetta("Coppie possibili", Tema.NASCITE);

    PannelloPopolazione() {
        setLayout(new BorderLayout(0, 8));
        setBackground(Tema.SFONDO);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel targhette = new JPanel();
        targhette.setLayout(new BoxLayout(targhette, BoxLayout.X_AXIS));
        targhette.setBackground(Tema.SFONDO);
        for (Targhetta t : List.of(tVive, tSessi, tCoppie, tPicco, tNascite, tMorti,
                tMaiVissute, tGenerazione)) {
            targhette.add(t);
            targhette.add(Box.createHorizontalStrut(8));
        }
        targhette.add(Box.createHorizontalGlue());

        add(targhette, BorderLayout.NORTH);
        add(grafico, BorderLayout.CENTER);
    }

    void aggiorna() {
        grafico.aggiorna();

        List<History.Sample> campioni = History.snapshot();
        int picco = 0;
        for (History.Sample s : campioni) {
            picco = Math.max(picco, s.popolazione);
        }
        int[] m = History.mortiPerCausa();

        tVive.set(Entity_manager.Entity_count() + " / " + Simulation.limiteDiSicurezza());
        tPicco.set(picco);
        tNascite.set(History.nasciteTotali());
        tMorti.set(m[0] + m[1] + m[2]);
        tMaiVissute.set(Entity_manager.snapshotAll().size());
        tGenerazione.set(generazioneMassima());

        int maschi = 0, femmine = 0, maschiMaturi = 0, femmineMature = 0;
        for (src.entity e : Entity_manager.snapshotAlive()) {
            if (e.getSex() == 2) {
                femmine++;
                if (e.isMature()) femmineMature++;
            } else {
                maschi++;
                if (e.isMature()) maschiMaturi++;
            }
        }
        tSessi.set(maschi + " / " + femmine);
        tCoppie.set(Math.min(maschiMaturi, femmineMature));
    }

    private static int generazioneMassima() {
        int max = 0;
        for (src.entity e : Entity_manager.snapshotAll()) {
            max = Math.max(max, e.getGenome().lineage().generation());
        }
        return max;
    }
}
