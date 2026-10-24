package src.body;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IL REGISTRATORE DI MOVIMENTO: tiene da parte come si e' mosso ogni corpo, e lo restituisce alla
 * prossima simulazione come punto di partenza.
 *
 * IL PROBLEMA CHE RISOLVE. Una fondatrice nasce con un'andatura inventata a tavolino
 * ({@link Gait#seed}) e ha il tempo di una vita per migliorarla, allenandosi mentre vive (vedi
 * Locomotion.practice). Quello che impara passa ai figli, ma quando la sessione finisce finisce
 * tutto: la sessione dopo ricomincia dallo stesso seme a tavolino, e le prime generazioni
 * rimuoiono senza saper camminare esattamente come la volta prima. Il mondo non accumula niente
 * tra una partita e l'altra, e il poco che l'evoluzione aveva trovato si butta.
 *
 * Qui invece ogni entita' lascia scritto, a fine vita, COME si muoveva e QUANTO si e' mossa
 * davvero: non il punteggio della fisica, ma i metri percorsi nel mondo e le mele che ha
 * raggiunto. La sessione successiva, invece del seme a tavolino, parte dall'andatura di chi si
 * era mosso meglio con un corpo simile.
 *
 * PERCHE' I METRI E NON IL PUNTEGGIO DELLA FISICA. Il punteggio della fisica
 * ({@link GaitEvaluator.Result#score}) misura una prova di due secondi su un pavimento piatto;
 * i metri percorsi misurano una vita intera su un terreno a gradini, con alberi in mezzo e con il
 * cervello che decide dove andare. Quello che si vuole conservare e' il secondo: un'andatura che
 * nel mondo ha portato qualcuno fino al cibo e' una prova migliore di una velocita' di punta
 * ottenuta in laboratorio. Le mele contano piu' dei metri perche' muoversi senza arrivare da
 * nessuna parte non e' ancora camminare.
 *
 * COME SI RITROVA L'ANDATURA GIUSTA. Un'andatura e' un parametro per grado di liberta': su un
 * corpo diverso non vale. Si indicizza quindi per CLASSE MORFOLOGICA (quanti appoggi, quanti
 * gradi di liberta', lunghezza della gamba arrotondata) e, quando una classe esatta non c'e', si
 * prende la piu' vicina e la si riadatta ({@link Gait#adaptTo}: i giunti con lo stesso nome
 * tengono i loro parametri, quelli nuovi partono dal seme). E' lo stesso meccanismo con cui un
 * figlio con un segmento in piu' usa l'andatura dei genitori.
 *
 * L'interruttore e' {@link #enabled}: a 0 le fondatrici ripartono dal seme a tavolino, cioe' il
 * braccio di controllo.
 */
public final class GaitArchive {

    /** Con false il registratore non scrive e non suggerisce niente: e' il braccio di controllo. */
    public static volatile boolean enabled = true;

    /** Dove vive l'archivio. Relativo alla cartella di lavoro, come logs/ e genealogy/. */
    public static volatile Path file = Path.of("andature", "archivio.json");

    /**
     * Quanto vale una mela raggiunta, in metri. Arrivare al cibo e' il traguardo vero (vedi
     * sopra), quindi pesa molto piu' di qualche metro percorso: con 25 una sola mela batte
     * venticinque metri di passeggiata che non e' finita da nessuna parte.
     */
    private static final double MELA_IN_METRI = 25.0;

    /** Larghezza delle classi di lunghezza della gamba (m): sotto questa differenza e' lo stesso corpo. */
    private static final double CLASSE_GAMBA = 0.08;

    /**
     * Una voce dell'archivio: un'andatura, il corpo su cui girava, e quanto si e' mossa nel mondo.
     *
     * @param distanza metri effettivamente percorsi nel mondo in tutta la vita
     * @param mele     cibo raggiunto (vedi MELA_IN_METRI)
     * @param vissuto  tick di vita: serve a rileggere l'archivio, non al confronto
     */
    public record Voce(String classe, int appoggi, int dof, double gamba, Gait andatura,
                       double distanza, int mele, int vissuto, double velocita, long entita,
                       int sessione) {

        /** Il merito e' la prova nel mondo: metri percorsi piu' le mele raggiunte. */
        public double merito() {
            return distanza + MELA_IN_METRI * mele;
        }
    }

    private static final Map<String, Voce> migliori = new ConcurrentHashMap<>();
    private static volatile boolean caricato = false;
    private static volatile int registrate = 0;
    private static volatile int sostituite = 0;
    private static volatile int suggerite = 0;
    private static volatile boolean daSalvare = false;

    private GaitArchive() {
    }

    // ------------------------------------------------------------------ chiave

    /**
     * La classe morfologica di un corpo: due corpi con la stessa classe usano la stessa andatura
     * senza riadattarla. Non e' la firma esatta di Locomotion.signature, che include la lunghezza
     * di ogni segmento: quella non si ripeterebbe mai tra due sessioni diverse, e un archivio che
     * non trova mai niente non serve a nulla.
     */
    public static String classeDi(Automaton body) {
        BodyTraits t = BodyTraits.of(body);
        int gamba = (int) Math.round(t.legLength() / CLASSE_GAMBA);
        return t.supportCount() + "a-" + body.dofCount() + "g-" + gamba + "l";
    }

    // ------------------------------------------------------------------ registrare

    /**
     * Registra come si e' mossa un'entita'. Si tiene solo la voce di MERITO maggiore per ogni
     * classe morfologica: l'archivio e' una raccolta di punti di partenza, non uno storico.
     *
     * Si chiama a fine vita (vedi Simulation.kill) e per i vivi alla chiusura della sessione: un
     * mondo che finisce con qualcuno ancora vivo e' proprio il caso in cui quel qualcuno aveva
     * l'andatura migliore di tutte.
     */
    public static void registra(Automaton adultBody, Gait andatura, double distanza, int mele,
                                int vissuto, double velocita, long entita, int sessione) {
        if (!enabled || andatura == null || adultBody == null) {
            return;
        }
        // un'entita' che non si e' mai mossa non ha niente da insegnare
        if (distanza <= 0 && mele <= 0) {
            return;
        }
        carica();
        BodyTraits t = BodyTraits.of(adultBody);
        String classe = classeDi(adultBody);
        Voce nuova = new Voce(classe, t.supportCount(), adultBody.dofCount(), t.legLength(),
                andatura, distanza, mele, vissuto, velocita, entita, sessione);
        registrate++;
        synchronized (GaitArchive.class) {
            Voce vecchia = migliori.get(classe);
            if (vecchia == null || nuova.merito() > vecchia.merito()) {
                migliori.put(classe, nuova);
                if (vecchia != null) {
                    sostituite++;
                }
                daSalvare = true;
            }
        }
    }

    // ------------------------------------------------------------------ suggerire

    /**
     * L'andatura da cui partire per questo corpo, o null se l'archivio non ha niente di
     * utilizzabile. Chi la riceve la tratta come un'andatura EREDITATA, perche' e' esattamente
     * quello che e': un'eredita' dalle sessioni precedenti invece che dai genitori.
     */
    public static Gait andaturaPer(Automaton body, Random rng) {
        if (!enabled) {
            return null;
        }
        carica();
        if (migliori.isEmpty()) {
            return null;
        }
        Voce v = migliori.get(classeDi(body));
        if (v == null) {
            v = piuVicina(body);
        }
        if (v == null) {
            return null;
        }
        suggerite++;
        // adaptTo tiene i giunti con lo stesso nome e riempie dal seme quelli che non c'erano:
        // su una classe esatta non cambia niente, su una vicina e' quello che la rende usabile
        return v.andatura().adaptTo(body, rng);
    }

    /**
     * La voce piu' vicina: prima a parita' di appoggi (un bipede non impara da un quadrupede),
     * poi per differenza di gradi di liberta' e di lunghezza della gamba. Se nessuno ha lo stesso
     * numero di appoggi non si suggerisce niente: adattare l'andatura di un corpo che si regge su
     * due zampe a uno che ne ha sei non e' un punto di partenza, e' rumore.
     */
    private static Voce piuVicina(Automaton body) {
        BodyTraits t = BodyTraits.of(body);
        Voce scelta = null;
        double meglio = Double.MAX_VALUE;
        for (Voce v : migliori.values()) {
            if (v.appoggi() != t.supportCount()) {
                continue;
            }
            double d = Math.abs(v.dof() - body.dofCount()) / 4.0
                    + Math.abs(v.gamba() - t.legLength()) / CLASSE_GAMBA;
            if (d < meglio) {
                meglio = d;
                scelta = v;
            }
        }
        return scelta;
    }

    // ------------------------------------------------------------------ su disco

    /** Carica l'archivio la prima volta che serve. Un archivio che non c'e' non e' un errore. */
    public static synchronized void carica() {
        if (caricato) {
            return;
        }
        caricato = true;    // anche se fallisce: non si riprova a ogni nascita
        try {
            if (!Files.exists(file)) {
                return;
            }
            JSONObject root = new JSONObject(Files.readString(file));
            JSONArray arr = root.optJSONArray("voci");
            if (arr == null) {
                return;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Voce v = new Voce(o.getString("classe"), o.getInt("appoggi"), o.getInt("dof"),
                        o.getDouble("gamba"), Gait.fromJson(o.getJSONObject("andatura")),
                        o.optDouble("distanza", 0), o.optInt("mele", 0), o.optInt("vissuto", 0),
                        o.optDouble("velocita", 0), o.optLong("entita", -1),
                        o.optInt("sessione", -1));
                migliori.put(v.classe(), v);
            }
            System.out.println("[andature] archivio: " + migliori.size()
                    + " classi di corpo da " + file);
        } catch (Exception e) {
            // un archivio illeggibile non deve impedire di giocare: si ricomincia a raccoglierlo
            System.out.println("[andature] archivio non leggibile (" + e + "): riparto da zero.");
        }
    }

    /**
     * Scrive l'archivio, se c'e' qualcosa di nuovo. Si scrive su un file temporaneo e poi si
     * sposta: un'interruzione a meta' scrittura lascerebbe l'archivio di tutte le sessioni
     * precedenti troncato a meta' riga.
     */
    public static synchronized void salva() {
        if (!enabled || !daSalvare || migliori.isEmpty()) {
            return;
        }
        try {
            JSONObject root = new JSONObject();
            root.put("versione", 1);
            JSONArray arr = new JSONArray();
            List<Voce> ordinate = new ArrayList<>(migliori.values());
            ordinate.sort((a, b) -> Double.compare(b.merito(), a.merito()));
            for (Voce v : ordinate) {
                JSONObject o = new JSONObject();
                o.put("classe", v.classe());
                o.put("appoggi", v.appoggi());
                o.put("dof", v.dof());
                o.put("gamba", v.gamba());
                o.put("distanza", v.distanza());
                o.put("mele", v.mele());
                o.put("vissuto", v.vissuto());
                o.put("velocita", v.velocita());
                o.put("entita", v.entita());
                o.put("sessione", v.sessione());
                o.put("andatura", v.andatura().toJson());
                arr.put(o);
            }
            root.put("voci", arr);
            Path dir = file.getParent();
            if (dir != null) {
                Files.createDirectories(dir);
            }
            Path tmp = Path.of(file + ".tmp");
            Files.writeString(tmp, root.toString(2));
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            daSalvare = false;
        } catch (IOException e) {
            System.out.println("[andature] non riesco a scrivere l'archivio: " + e);
        }
    }

    // ------------------------------------------------------------------ diagnostica

    public static Collection<Voce> voci() {
        carica();
        return migliori.values();
    }

    public static String stats() {
        carica();
        Voce migliore = null;
        for (Voce v : migliori.values()) {
            if (migliore == null || v.merito() > migliore.merito()) {
                migliore = v;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("andature registrate: ").append(migliori.size()).append(" classi di corpo, ")
          .append(registrate).append(" vite registrate (").append(sostituite)
          .append(" primati battuti), ").append(suggerite).append(" andature suggerite");
        if (migliore != null) {
            sb.append(String.format(Locale.ROOT,
                    "%nla migliore: classe %s, %.1f m percorsi e %d mele (merito %.1f), "
                            + "%.3f m/s misurati",
                    migliore.classe(), migliore.distanza(), migliore.mele(), migliore.merito(),
                    migliore.velocita()));
        }
        return sb.toString();
    }

    /** Per i test e per ricominciare da zero senza cancellare il file a mano. */
    public static synchronized void svuota() {
        migliori.clear();
        caricato = false;
        registrate = 0;
        sostituite = 0;
        suggerite = 0;
        daSalvare = false;
    }
}
