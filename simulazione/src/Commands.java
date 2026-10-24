package src;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * I comandi della simulazione: un registro, non uno switch.
 *
 * PERCHE' RISCRITTO. Prima i comandi erano un grosso switch su una stringa, con l'elenco di
 * "help" tenuto a mano in una mappa a parte. Tre problemi concreti, tutti presenti:
 *  - l'elenco e lo switch potevano divergere, e divergevano (comandi documentati e non esistenti,
 *    e viceversa);
 *  - l'analisi degli argomenti era dentro ogni case, quindi "layer" senza argomento lanciava
 *    NumberFormatException invece di dire come si usa, e "Get 5" trattava l'id come INDICE nella
 *    lista delle vive - cioe' mostrava l'entita' sbagliata;
 *  - i nomi erano incoerenti ("Get", "NewStart", "Save" con la maiuscola, il resto minuscolo) e
 *    il confronto era sensibile alle maiuscole, quindi "get" non funzionava.
 * Qui ogni comando e' un oggetto con nome, sinonimi, uso, descrizione e categoria, e la stessa
 * struttura serve a eseguire, a stampare l'aiuto e a completare a tab nella GUI: non ci sono due
 * liste da tenere allineate perche' non c'e' che una lista.
 *
 * GLI ERRORI DI USO SONO RISPOSTE, NON ECCEZIONI: un comando scritto male stampa il suo uso. La
 * console e la GUI chiamano {@link #run} e ricevono sempre una stringa da mostrare.
 */
public final class Commands {

    /** Un comando: tutto cio' che serve per eseguirlo, documentarlo e completarlo. */
    public static final class Command {
        public final String name;
        public final List<String> aliases;
        /** Come si scrive, argomenti compresi: e' anche il messaggio d'errore. */
        public final String usage;
        public final String description;
        public final String category;
        private final Function<String[], String> handler;

        Command(String name, String[] aliases, String usage, String description,
                String category, Function<String[], String> handler) {
            this.name = name;
            this.aliases = List.of(aliases);
            this.usage = usage;
            this.description = description;
            this.category = category;
            this.handler = handler;
        }
    }

    /** Errore d'uso: viene trasformato nel messaggio d'uso del comando. */
    static final class Uso extends RuntimeException {
        Uso(String messaggio) {
            super(messaggio);
        }
    }

    private static final Map<String, Command> BY_NAME = new LinkedHashMap<>();
    private static final List<Command> ORDER = new ArrayList<>();

    private Commands() {
    }

    private static void add(String name, String[] aliases, String usage, String description,
                            String category, Function<String[], String> handler) {
        Command c = new Command(name, aliases, usage, description, category, handler);
        ORDER.add(c);
        BY_NAME.put(name.toLowerCase(Locale.ROOT), c);
        for (String a : aliases) {
            BY_NAME.put(a.toLowerCase(Locale.ROOT), c);
        }
    }

    private static final String[] NONE = {};

    // ------------------------------------------------------------------ lettura degli argomenti

    private static int intArg(String[] a, int i, String nome) {
        if (i >= a.length) {
            throw new Uso("manca " + nome);
        }
        try {
            return Integer.parseInt(a[i].trim());
        } catch (NumberFormatException e) {
            throw new Uso(nome + " deve essere un numero intero, non \"" + a[i] + "\"");
        }
    }

    private static double doubleArg(String[] a, int i, String nome) {
        if (i >= a.length) {
            throw new Uso("manca " + nome);
        }
        try {
            // la virgola decimale e' quello che un italiano scrive: accettarla costa una riga
            return Double.parseDouble(a[i].trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new Uso(nome + " deve essere un numero, non \"" + a[i] + "\"");
        }
    }

    private static long longArg(String[] a, int i, String nome) {
        if (i >= a.length) {
            throw new Uso("manca " + nome);
        }
        try {
            return Long.parseLong(a[i].trim());
        } catch (NumberFormatException e) {
            throw new Uso(nome + " deve essere un numero intero, non \"" + a[i] + "\"");
        }
    }

    private static boolean onOff(String[] a, int i, boolean seManca) {
        if (i >= a.length) {
            return seManca;
        }
        String v = a[i].trim().toLowerCase(Locale.ROOT);
        if (v.equals("on") || v.equals("si") || v.equals("true") || v.equals("1")) {
            return true;
        }
        if (v.equals("off") || v.equals("no") || v.equals("false") || v.equals("0")) {
            return false;
        }
        throw new Uso("scrivi on oppure off, non \"" + a[i] + "\"");
    }

    /**
     * L'entita' con quell'ID. Attenzione: per ID, non per posizione nella lista - il comando
     * vecchio usava Entity_get(indice) e mostrava l'entita' sbagliata appena qualcuna moriva.
     */
    /**
     * L'archivio delle andature: cosa si e' conservato delle sessioni precedenti. Ordinato per
     * merito (metri percorsi piu' le mele raggiunte), che e' il criterio con cui si scelgono i
     * punti di partenza - vedi src.body.GaitArchive.
     */
    private static String andature() {
        StringBuilder sb = new StringBuilder(src.body.GaitArchive.stats());
        java.util.List<src.body.GaitArchive.Voce> voci =
                new java.util.ArrayList<>(src.body.GaitArchive.voci());
        if (voci.isEmpty()) {
            sb.append(String.format("%n(nessuna andatura registrata: e' la prima sessione, o l'archivio e' spento)"));
            return sb.toString();
        }
        voci.sort((x, y) -> Double.compare(y.merito(), x.merito()));
        sb.append(String.format("%n%-16s %5s %5s %7s %7s %5s %7s %8s %6s",
                "classe", "appog", "dof", "gamba", "metri", "mele", "merito", "m/s", "sess"));
        for (src.body.GaitArchive.Voce v : voci) {
            sb.append(String.format("%n%-16s %5d %5d %7.2f %7.1f %5d %7.1f %8.3f %6d",
                    v.classe(), v.appoggi(), v.dof(), v.gamba(), v.distanza(), v.mele(),
                    v.merito(), v.velocita(), v.sessione()));
        }
        return sb.toString();
    }

    private static entity byId(String[] a, int i) {
        int id = intArg(a, i, "l'id dell'entita'");
        for (entity e : Entity_manager.snapshotAll()) {
            if (e.getId() == id) {
                return e;
            }
        }
        throw new Uso("nessuna entita' con id " + id);
    }

    private static int colonna(String[] a, int i, String nome) {
        int v = intArg(a, i, nome);
        if (v < 0 || v >= world.getDim()) {
            throw new Uso(nome + " deve stare fra 0 e " + (world.getDim() - 1));
        }
        return v;
    }

    // ================================================================== il registro

    static {
        // ---------------------------------------------------------- simulazione
        add("help", new String[]{"aiuto", "?"}, "help [comando]",
                "Elenco dei comandi, oppure l'uso di un comando solo", "simulazione",
                a -> a.length > 0 ? helpOf(a[0]) : helpAll());

        add("newstart", new String[]{"nuova"}, "newstart",
                "Nuova simulazione: mondo nuovo, fondatrici, nuova sessione genealogica",
                "simulazione", a -> {
                    entity.setMaxFood(entity.MAX_FOOD);   // "riserva" puo' averla cambiata
                    console.startSimulation(true);
                    return "Simulazione avviata (seed " + Simulation.getSeed() + ")";
                });

        add("start", new String[]{"riprendi"}, "start", "Riprende il ciclo se era in pausa",
                "simulazione", a -> {
                    world.PauseCycle(false);
                    return "Ciclo in esecuzione";
                });

        add("stop", new String[]{"pausa"}, "stop", "Mette in pausa il ciclo",
                "simulazione", a -> {
                    world.PauseCycle(true);
                    return "Ciclo in pausa";
                });

        add("speed", new String[]{"velocita"}, "speed <fattore>",
                "Velocita' della simulazione (1 = normale, " + (int) Simulation.MAX_TIME_SCALE
                        + " = massima)", "simulazione", a -> {
                    Simulation.setTimeScale(doubleArg(a, 0, "il fattore"));
                    return "Velocita': x" + Simulation.getTimeScale();
                });

        add("seed", NONE, "seed [numero]",
                "Seed della simulazione; vale dal prossimo newstart", "simulazione", a -> {
                    if (a.length == 0) {
                        return "Seed attuale: " + Simulation.getSeed();
                    }
                    Simulation.setSeed(longArg(a, 0, "il seed"));
                    return "Seed " + Simulation.getSeed() + " (dal prossimo newstart)";
                });

        add("stato", new String[]{"status", "info"}, "stato",
                "Riepilogo: tick, popolazione, cibo, bosco, ora del giorno", "simulazione",
                a -> stato());

        add("save", new String[]{"salva"}, "save", "Salva mondo, entita' e genealogia",
                "simulazione", a -> Saving.Save() ? "Salvato" : "Salvataggio non riuscito");

        add("load", new String[]{"carica"}, "load <n>",
                "Carica il salvataggio numero n (senza argomento: elenco)", "simulazione", a -> {
                    if (a.length == 0) {
                        return elencoSalvataggi();
                    }
                    int n = intArg(a, 0, "il numero del salvataggio");
                    return Saving.Load(n) ? "Caricato il salvataggio " + n
                            : "Caricamento non riuscito (vedi il log)";
                });

        add("passo", new String[]{"step"}, "passo [n]",
                "Esegue n tick a mano, anche in pausa (default 1)", "simulazione", a -> {
                    int n = a.length > 0 ? intArg(a, 0, "quanti tick") : 1;
                    if (n < 1 || n > 10000) {
                        throw new Uso("i tick da eseguire vanno da 1 a 10000");
                    }
                    int fatti = Simulation.passo(n);
                    return fatti + " tick eseguiti (adesso " + Simulation.getTick() + ")"
                            + (world.isPaused() ? "" : " - attenzione: il ciclo non e' in pausa, "
                                    + "questi si sommano ai suoi");
                });

        add("storia", new String[]{"andamento"}, "storia [n]",
                "Gli ultimi n campioni della storia: popolazione, nascite, morti (default 15)",
                "simulazione", a -> storia(a.length > 0 ? intArg(a, 0, "quanti") : 15));

        // ---------------------------------------------------------- mondo
        add("terrain", new String[]{"terreno"}, "terrain [flat]",
                "Terreno piatto o irregolare; vale dal prossimo newstart", "mondo", a -> {
                    boolean flat = a.length > 0 && (a[0].equalsIgnoreCase("flat")
                            || a[0].equalsIgnoreCase("piatto"));
                    world.setFlatTerrain(flat);
                    return (flat ? "Terreno piatto" : "Terreno irregolare")
                            + " dal prossimo newstart";
                });

        add("layer", new String[]{"livello"}, "layer <n>",
                "Quota mostrata dalla stampa testuale della mappa", "mondo", a -> {
                    int n = colonna(a, 0, "la quota");
                    world.ChengePrintGround(n);
                    return "Quota di stampa: " + n;
                });

        add("mappa", new String[]{"planet"}, "mappa",
                "Stampa la mappa a caratteri (acqua, alberi, mele, entita')", "mondo", a -> {
                    world.planetPrint();
                    return "Mappa stampata sul log";
                });

        // ---------------------------------------------------------- bosco
        add("flora", new String[]{"bosco"}, "flora [on|off]",
                "Stato del bosco, oppure accende/spegne alberi e mele", "bosco", a -> {
                    if (a.length == 0) {
                        return Flora.report();
                    }
                    Flora.enabled = onOff(a, 0, true);
                    return "Flora " + (Flora.enabled ? "attiva" : "disattivata")
                            + " (dal prossimo newstart)";
                });

        add("albero", new String[]{"tree+"}, "albero <x> <z>",
                "Pianta un albero adulto in quella colonna", "bosco", a -> {
                    int x = colonna(a, 0, "x");
                    int z = colonna(a, 1, "z");
                    Flora.Tree t = Flora.plantTree(x, z, Simulation.getTick());
                    if (t == null) {
                        return "Non si puo' piantare in " + x + "," + z
                                + " (acqua, oppure troppo vicino a un altro tronco)";
                    }
                    return String.format(Locale.ROOT,
                            "Albero #%d in %d,%d: altezza %.2f m (max %.2f), chioma %.2f m, "
                            + "produzione %.2f, velocita' %.2f",
                            t.id, x, z, t.height(), t.maxHeight, t.crownRadius,
                            t.production, t.growthSpeed);
                });

        add("mela", new String[]{"apple"}, "mela <x> <z> [maturazione]",
                "Mette una mela a terra (maturazione 0 verde, 1 matura)", "bosco", a -> {
                    int x = colonna(a, 0, "x");
                    int z = colonna(a, 1, "z");
                    double g = a.length > 2 ? doubleArg(a, 2, "la maturazione") : 1.0;
                    if (!Flora.enabled) {
                        world.add(x, world.surfaceY(x, z), z, "M");
                        return "Mela in " + x + "," + z + " (flora disattivata: mela semplice)";
                    }
                    Flora.Apple m = Flora.plantApple(x, z, g);
                    return m == null ? "Non si puo' mettere una mela in " + x + "," + z + " (acqua)"
                            : String.format(Locale.ROOT, "Mela #%d in %d,%d: %s, nutre %.2f",
                                    m.id, x, z, m.stage(), m.nutrition());
                });

        add("alberi", new String[]{"bosco+"}, "alberi [n]",
                "Gli alberi, i piu' alti per primi (default 15)", "bosco",
                a -> elencoAlberi(a.length > 0 ? intArg(a, 0, "quanti") : 15));

        add("mele", new String[]{"frutti"}, "mele [n]",
                "Le mele, le piu' nutrienti per prime (default 15)", "bosco",
                a -> elencoMele(a.length > 0 ? intArg(a, 0, "quante") : 15));

        // ---------------------------------------------------------- giorno e notte
        add("ora", new String[]{"luce"}, "ora [frazione|alba|giorno|tramonto|notte]",
                "Legge l'ora del giorno, oppure la sposta (0 mezzanotte, 0.25 mezzogiorno)",
                "giorno", a -> {
                    if (a.length > 0) {
                        /*
                         * SPOSTA IL SOLE, NON IL TEMPO. Serve a provare un comportamento
                         * notturno senza aspettare mezza giornata di simulazione: cambia solo
                         * lo scostamento dell'ora (vedi DayNight.OFFSET), quindi l'eta' delle
                         * entita' e la maturazione delle mele restano quelle che sono.
                         */
                        double f = switch (a[0].trim().toLowerCase(Locale.ROOT)) {
                            case "alba" -> 0.0;
                            case "giorno", "mezzogiorno" -> 0.25;
                            case "tramonto" -> 0.5;
                            case "notte", "mezzanotte" -> 0.75;
                            default -> doubleArg(a, 0, "la frazione del giorno");
                        };
                        DayNight.enabled = true;
                        DayNight.setPhase(f);
                    }
                    return String.format(Locale.ROOT,
                            "%s - luce %.2f, fase del giorno %.0f%% (giornata di %d tick, ciclo %s)",
                            DayNight.phaseName(), DayNight.daylight(),
                            DayNight.phase(Simulation.getTick()) * 100,
                            DayNight.DAY_TICKS, DayNight.enabled ? "attivo" : "spento");
                });

        add("ciclo", new String[]{"giornata"}, "ciclo <tick>|on|off",
                "Durata della giornata in tick, oppure spegne il ciclo (luce fissa)", "giorno",
                a -> {
                    if (a.length == 0) {
                        throw new Uso("manca la durata, oppure on/off");
                    }
                    String v = a[0].trim().toLowerCase(Locale.ROOT);
                    if (v.equals("on") || v.equals("off")) {
                        DayNight.enabled = v.equals("on");
                        return "Ciclo del giorno " + (DayNight.enabled ? "attivo"
                                : "spento (luce sempre al massimo)");
                    }
                    int t = intArg(a, 0, "la durata in tick");
                    if (t < 20) {
                        throw new Uso("una giornata sotto i 20 tick non lascia il tempo di fare niente");
                    }
                    DayNight.DAY_TICKS = t;
                    DayNight.enabled = true;
                    return "Giornata di " + t + " tick";
                });

        // ---------------------------------------------------------- entita'
        add("entita", new String[]{"get", "e"}, "entita <id>",
                "Scheda completa di un'entita'", "entita", a -> scheda(byId(a, 0)));

        add("vive", new String[]{"alive"}, "vive [n]",
                "Le entita' vive, le piu' anziane per prime (default 20)", "entita",
                a -> elencoVive(a.length > 0 ? intArg(a, 0, "quante") : 20));

        add("morte", new String[]{"dead"}, "morte [n]",
                "Le ultime entita' morte, con la causa (default 20)", "entita",
                a -> elencoMorte(a.length > 0 ? intArg(a, 0, "quante") : 20));

        add("morti", new String[]{"cause"}, "morti",
                "Conteggio dei morti per causa", "entita", a -> {
                    int[] m = History.mortiPerCausa();
                    int tot = m[0] + m[1] + m[2];
                    StringBuilder sb = new StringBuilder("Morti: " + tot + " in tutto\n");
                    for (int i = 0; i < History.CAUSE.length; i++) {
                        sb.append(String.format(Locale.ROOT, "  %-14s %5d  (%.0f%%)%n",
                                History.CAUSE[i], m[i], tot == 0 ? 0 : 100.0 * m[i] / tot));
                    }
                    sb.append("Nascite: ").append(History.nasciteTotali());
                    return sb.toString();
                });

        add("azioni", new String[]{"comportamento"}, "azioni",
                "Quante volte e' stata scelta ciascuna azione da inizio sessione", "entita",
                a -> azioni());

        add("dieta", NONE, "dieta [adatta <n>]",
                "Dove sta andando l'abitudine alimentare della popolazione", "entita",
                a -> {
                    if (a.length >= 2 && "adatta".equalsIgnoreCase(a[0])) {
                        ActionWeights.dietAdapt = Math.max(0, Double.parseDouble(a[1]));
                    }
                    int erb = 0, onn = 0, carn = 0;
                    double somma = 0, spostamento = 0;
                    for (entity e : Entity_manager.snapshotAlive()) {
                        switch (e.dietNow()) {
                            case HERBIVORE -> erb++;
                            case OMNIVORE -> onn++;
                            default -> carn++;
                        }
                        somma += e.getDietBias();
                        spostamento += e.getDietBias() - e.getStats().dietBias;
                    }
                    int vivi = erb + onn + carn;
                    if (vivi == 0) {
                        return "Nessuna entita' viva.";
                    }
                    return String.format(Locale.ROOT,
                            "Adattamento %.3f per pasto, resa da %.2f a %.2f%n"
                            + "Vive: %d erbivore, %d onnivore, %d carnivore  (%.0f%% carnivore)%n"
                            + "Abitudine media %.3f (0 vegetale, 1 carne), spostata di %+.3f dal gene",
                            ActionWeights.dietAdapt, ActionWeights.dietEffMin,
                            ActionWeights.dietEffMax,
                            erb, onn, carn, 100.0 * carn / vivi,
                            somma / vivi, spostamento / vivi);
                });

        add("sessi", new String[]{"sesso"}, "sessi",
                "Maschi e femmine vivi, quanti in eta' da riproduzione, e i morti per sesso",
                "entita", a -> sessi());

        add("geni", new String[]{"genoma"}, "geni <id>",
                "Tutti i geni di un'entita' con i due alleli e il valore risolto", "entita",
                a -> geni(byId(a, 0)));

        add("corpo", new String[]{"body"}, "corpo <id>",
                "Misure del corpo e andatura di un'entita'", "entita", a -> corpo(byId(a, 0)));

        add("andature", new String[]{"archivio"}, "andature",
                "Il registratore di movimento: quali andature sono state conservate e da chi",
                "entita", a -> andature());

        add("tree", new String[]{"genealogia"}, "tree [id]",
                "Riepilogo della genealogia, oppure gli antenati di un'entita'", "entita",
                a -> a.length > 0 ? Genealogy.describe(intArg(a, 0, "l'id"))
                        : Genealogy.summary());

        add("setfood", new String[]{"cibo"}, "setfood <id> <valore>",
                "Imposta il cibo di un'entita'", "entita", a -> {
                    entity e = byId(a, 0);
                    int v = intArg(a, 1, "il valore");
                    e.setFood(v);
                    return "Entita' #" + e.getId() + ": cibo " + e.getFood();
                });

        add("setthirst", new String[]{"sete"}, "setthirst <id> <valore>",
                "Imposta l'acqua di un'entita'", "entita", a -> {
                    entity e = byId(a, 0);
                    int v = intArg(a, 1, "il valore");
                    e.setThirst(v);
                    return "Entita' #" + e.getId() + ": acqua " + e.getThirst();
                });

        add("uccidi", new String[]{"kill"}, "uccidi <id>",
                "Uccide un'entita' (causa: comando)", "entita", a -> {
                    entity e = byId(a, 0);
                    if (!e.isAlive()) {
                        return "Entita' #" + e.getId() + " e' gia' morta";
                    }
                    Simulation.kill(e, "comando");
                    return "Entita' #" + e.getId() + " uccisa";
                });
        // ---------------------------------------------------------- interfaccia
        add("scheda", new String[]{"apri"}, "scheda <id>",
                "Apre nella GUI la scheda di un'entita' (statistiche, geni, genealogia)",
                "interfaccia", a -> {
                    entity e = byId(a, 0);
                    if (!src.gui.Gui.isOpen()) {
                        return "La finestra di controllo non e' aperta: prima 'gui on'";
                    }
                    src.gui.Gui.selezionaEntita(e.getId());
                    src.gui.Gui.apriScheda(e.getId());
                    return "Scheda di #" + e.getId() + " aperta";
                });

        // Non c'e' un "gui off": chiudere la finestra di controllo termina la simulazione
        // (vedi Gui), quindi un comando per chiuderla sarebbe un comando per uscire travestito.
        add("gui", new String[]{"finestra"}, "gui",
                "Apre (o porta davanti) la finestra di controllo: liste, grafici, comandi",
                "interfaccia", a -> {
                    src.gui.Gui.start();
                    return "Finestra di controllo aperta";
                });

        add("riserva", new String[]{"maxfood"}, "riserva <n>",
                "Riserva massima di cibo delle entita' (standard " + entity.MAX_FOOD
                        + "); vale per quelle che nascono dopo", "entita", a -> {
                    entity.setMaxFood(intArg(a, 0, "la riserva"));
                    return "Riserva massima di cibo: " + entity.getMaxFood();
                });
    }

    // ================================================================== esecuzione

    /** Esegue una riga di comando e ritorna il testo da mostrare. Non lancia mai. */
    public static String run(String line) {
        if (line == null || line.trim().isEmpty()) {
            return "";
        }
        String[] parts = line.trim().split("\\s+");
        Command c = BY_NAME.get(parts[0].toLowerCase(Locale.ROOT));
        if (c == null) {
            return sconosciuto(parts[0]);
        }
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        try {
            String out = c.handler.apply(args);
            return out == null ? "" : out;
        } catch (Uso u) {
            return "Uso: " + c.usage + "\n  " + u.getMessage();
        } catch (Exception e) {
            // un comando che scoppia non deve portarsi via la console
            return "Errore in \"" + c.name + "\": " + e;
        }
    }

    /**
     * Comando sconosciuto: si suggerisce il piu' simile invece di dire solo "non esiste".
     * La distanza e' quella di Levenshtein sul nome; sopra un terzo della lunghezza non si
     * suggerisce niente, perche' un suggerimento sbagliato e' peggio di nessun suggerimento.
     */
    private static String sconosciuto(String nome) {
        String migliore = null;
        int best = Integer.MAX_VALUE;
        for (String k : BY_NAME.keySet()) {
            int d = distanza(nome.toLowerCase(Locale.ROOT), k);
            if (d < best) {
                best = d;
                migliore = k;
            }
        }
        String out = "Comando sconosciuto: \"" + nome + "\"";
        if (migliore != null && best <= Math.max(1, nome.length() / 3)) {
            out += ". Forse volevi \"" + migliore + "\"?";
        }
        return out + "\nScrivi help per l'elenco.";
    }

    private static int distanza(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + costo);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /** I comandi, nell'ordine di registrazione: serve all'aiuto e alla GUI. */
    public static List<Command> all() {
        return List.copyOf(ORDER);
    }

    /** Nomi che cominciano per prefix, per il completamento a tab della GUI. */
    public static List<String> complete(String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Command c : ORDER) {
            if (c.name.startsWith(p)) {
                out.add(c.name);
            }
        }
        if (out.isEmpty()) {
            for (String k : BY_NAME.keySet()) {
                if (k.startsWith(p)) {
                    out.add(k);
                }
            }
        }
        return out;
    }

    // ================================================================== aiuto

    private static String helpAll() {
        StringBuilder sb = new StringBuilder("Comandi disponibili (help <comando> per i dettagli):\n");
        String cat = null;
        for (Command c : ORDER) {
            if (!c.category.equals(cat)) {
                cat = c.category;
                sb.append("\n[").append(cat).append("]\n");
            }
            sb.append(String.format("  %-28s %s%n", c.usage, c.description));
        }
        return sb.toString();
    }

    private static String helpOf(String nome) {
        Command c = BY_NAME.get(nome.toLowerCase(Locale.ROOT));
        if (c == null) {
            return sconosciuto(nome);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(c.usage).append("\n  ").append(c.description).append('\n');
        sb.append("  categoria: ").append(c.category);
        if (!c.aliases.isEmpty()) {
            sb.append("\n  sinonimi: ").append(String.join(", ", c.aliases));
        }
        return sb.toString();
    }

    // ================================================================== rapporti

    private static String stato() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tick ").append(Simulation.getTick())
          .append("  -  ").append(Simulation.isRunning()
                  ? (world.isPaused() ? "in pausa" : "in esecuzione") : "ferma")
          .append("  -  velocita' x").append(Simulation.getTimeScale())
          .append("  -  seed ").append(Simulation.getSeed()).append('\n');
        sb.append("Entita' vive: ").append(Entity_manager.Entity_count())
          .append(" / tetto ").append(Simulation.maxPopulation())
          .append("  -  mai vissute: ").append(Entity_manager.snapshotAll().size()).append('\n');
        int[] m = History.mortiPerCausa();
        sb.append("Nascite ").append(History.nasciteTotali())
          .append("  -  morti ").append(m[0] + m[1] + m[2])
          .append(" (fame ").append(m[0]).append(", sete ").append(m[1])
          .append(", combattimento ").append(m[2]).append(")\n");
        sb.append("Mele ").append(world.countApples());
        if (Flora.enabled) {
            sb.append(" (").append(Flora.countOnTree()).append(" sugli alberi)")
              .append("  -  alberi ").append(Flora.countTrees())
              .append("  -  semi ").append(Flora.countSeeds());
        }
        sb.append('\n').append(DayNight.phaseName())
          .append(String.format(Locale.ROOT, " (luce %.2f)", DayNight.daylight()));
        sb.append("  -  mondo ").append(world.getDim()).append('x').append(world.getDim())
          .append(", acqua a quota ").append(world.getWaterLevel());
        return sb.toString();
    }

    private static String scheda(entity e) {
        StringBuilder sb = new StringBuilder();
        sb.append("Entita' #").append(e.getId())
          .append(e.isAlive() ? " (viva)" : " (morta: " + e.getDeathCause()
                  + " al tick " + e.getDeathTick() + ")").append('\n');
        sb.append(String.format(Locale.ROOT,
                "generazione %d, eta' %d, sesso %s, dieta %s%n",
                e.getGenome().lineage().generation(), e.getAge(),
                e.getSex() == 2 ? "F" : "M",
                String.format(Locale.ROOT, "%s (%.2f, gene %.2f)",
                        e.dietNow(), e.getDietBias(), e.getStats().dietBias)));
        sb.append(String.format(Locale.ROOT,
                "salute %d/%d, cibo %d, acqua %d, stamina %.0f/%.0f%s%n",
                e.getHealt(), e.getMaxHealth(), e.getFood(), e.getThirst(),
                e.getStamina(), e.getStats().maxStamina, e.isSleeping() ? " (dorme)" : ""));
        sb.append(String.format(Locale.ROOT,
                "mele mangiate %d, pasti %d, uccisioni %d, ricompensa %.1f%n",
                e.getApplesEaten(), e.getFoodConsumed(), e.getKills(), e.getNetreward()));
        int[] p = e.getPos();
        sb.append(String.format(Locale.ROOT,
                "posizione %d,%d,%d (esatta %.2f, %.2f), guarda a %.0f gradi%n",
                p[0], p[1], p[2], e.getExactX(), e.getExactZ(),
                Math.toDegrees(e.getGazeDirection())));
        sb.append(String.format(Locale.ROOT,
                "vista %.1f celle adesso (gene %.1f), campo %.0f gradi, visione notturna %.2f%n",
                e.sightNow(), e.getStats().sightDistance, e.getStats().fieldOfView,
                e.getStats().nightVision));
        sb.append(String.format(Locale.ROOT,
                "arriva in alto a %.2f m, portata %.2f m, passo %.2f m",
                e.pickHeight(), e.reachNow(), e.stepLength()));
        return sb.toString();
    }

    private static String geni(entity e) {
        StringBuilder sb = new StringBuilder("Geni di #" + e.getId() + "\n");
        var schema = entity.getSchema();
        for (var g : schema.allGenes()) {
            var all = e.getGenome().get(g.id);
            if (all == null) {
                continue;
            }
            double risolto = src.genetics.PhenotypeResolver.resolve(g.id, e.getGenome(), schema);
            sb.append(String.format(Locale.ROOT, "  %-26s %7.3f   (alleli %.3f / %.3f)%n",
                    g.id, risolto, all.alleleA, all.alleleB));
        }
        return sb.toString();
    }

    private static String corpo(entity e) {
        var t = e.getTraits();
        StringBuilder sb = new StringBuilder("Corpo di #" + e.getId() + "\n");
        sb.append(String.format(Locale.ROOT,
                "  massa %.1f kg, altezza %.2f m, portata %.2f m, gamba %.2f m%n",
                t.mass(), t.height(), t.reach(), t.legLength()));
        sb.append(String.format(Locale.ROOT,
                "  appoggi %d, estremita' %d, %s%n",
                t.supportCount(), t.extremityCount(), t.crawls() ? "striscia" : "cammina"));
        var prof = e.getLocomotion();
        sb.append(prof == null ? "  andatura non ancora misurata"
                : String.format(Locale.ROOT, "  andatura: %.3f m/s misurati", e.getWalkSpeed()));
        return sb.toString();
    }

    /**
     * La storia a campioni, in testo: la stessa cosa che il grafico della GUI disegna.
     *
     * I MORTI SI MOSTRANO PER INTERVALLO, non cumulativi come arrivano da History: in una
     * tabella letta riga per riga interessa "quanti ne sono morti da quel campione a questo",
     * mentre la somma dall'inizio la si ha dal comando "morti". E' la differenza che il grafico
     * fa guardandola, e che qui va scritta.
     */
    private static String storia(int quanti) {
        List<History.Sample> tutti = History.snapshot();
        if (tutti.isEmpty()) {
            return "Nessun campione: la simulazione non e' mai partita in questa sessione";
        }
        int da = Math.max(0, tutti.size() - Math.max(1, quanti));
        StringBuilder sb = new StringBuilder("Storia (un campione ogni "
                + History.SAMPLE_INTERVAL + " tick, " + tutti.size() + " in tutto)\n");
        sb.append(String.format("  %-8s %-5s %-8s %-6s %-6s %-6s %-6s %-6s %s%n",
                "tick", "vive", "nascite", "fame", "sete", "lotta", "mele", "alberi", "luce"));
        int[] prima = null;
        for (int i = da; i < tutti.size(); i++) {
            History.Sample c = tutti.get(i);
            int[] m = c.morti;
            int[] base = prima != null ? prima : (i > 0 ? tutti.get(i - 1).morti : new int[3]);
            sb.append(String.format(Locale.ROOT,
                    "  %-8d %-5d %-8d %-6d %-6d %-6d %-6d %-6d %.2f%n",
                    c.tick, c.popolazione, c.nascite,
                    m[History.FAME] - base[History.FAME],
                    m[History.SETE] - base[History.SETE],
                    m[History.COMBATTIMENTO] - base[History.COMBATTIMENTO],
                    c.mele, c.alberi, c.luce));
            prima = m;
        }
        return sb.toString();
    }

    private static String elencoAlberi(int quanti) {
        if (!Flora.enabled) {
            return "Flora disattivata: non ci sono alberi (flora on, poi newstart)";
        }
        List<Flora.Tree> alberi = new ArrayList<>(Flora.snapshotTrees());
        alberi.sort((x, y) -> Double.compare(y.height(), x.height()));
        java.util.Map<Integer, Integer> frutti = new java.util.HashMap<>();
        for (Flora.Apple m : Flora.snapshotApples()) {
            if (m.onTree) {
                frutti.merge(m.treeId, 1, Integer::sum);
            }
        }
        StringBuilder sb = new StringBuilder("Alberi: " + alberi.size() + "\n");
        sb.append(String.format("  %-6s %-9s %-8s %-8s %-8s %-10s %s%n",
                "id", "x,z", "altezza", "max", "chioma", "maturita'", "mele appese"));
        for (int i = 0; i < Math.min(quanti, alberi.size()); i++) {
            Flora.Tree t = alberi.get(i);
            sb.append(String.format(Locale.ROOT,
                    "  #%-5d %-9s %-8.2f %-8.2f %-8.2f %-10s %d%n",
                    t.id, t.x + "," + t.z, t.height(), t.maxHeight, t.crownNow(),
                    String.format(Locale.ROOT, "%.0f%%", t.maturity() * 100),
                    frutti.getOrDefault(t.id, 0)));
        }
        if (alberi.size() > quanti) {
            sb.append("  ... e altri ").append(alberi.size() - quanti);
        }
        return sb.toString();
    }

    private static String elencoMele(int quante) {
        if (!Flora.enabled) {
            return "Flora disattivata: le mele sono celle del mondo, vedi 'mappa'";
        }
        List<Flora.Apple> mele = new ArrayList<>(Flora.snapshotApples());
        mele.sort((x, y) -> Double.compare(y.nutrition(), x.nutrition()));
        StringBuilder sb = new StringBuilder("Mele: " + mele.size()
                + " (" + Flora.countOnTree() + " appese)\n");
        sb.append(String.format("  %-6s %-6s %-14s %-9s %-10s %-8s %s%n",
                "id", "albero", "x,z", "quota", "stato", "nutre", "dove"));
        for (int i = 0; i < Math.min(quante, mele.size()); i++) {
            Flora.Apple m = mele.get(i);
            sb.append(String.format(Locale.ROOT,
                    "  #%-5d #%-5d %-14s %-9.2f %-10s %-8.2f %s%n",
                    m.id, m.treeId, String.format(Locale.ROOT, "%.1f,%.1f", m.x, m.z),
                    m.y, m.stage(), m.nutrition(), m.onTree ? "sull'albero" : "a terra"));
        }
        if (mele.size() > quante) {
            sb.append("  ... e altre ").append(mele.size() - quante);
        }
        return sb.toString();
    }

    /**
     * Il quadro dei sessi.
     *
     * PERCHE' SERVE UN COMANDO APPOSTA. Una popolazione puo' morire con il bosco pieno di mele
     * semplicemente perche' i superstiti sono tutti dello stesso sesso: la riproduzione vuole due
     * entita' di sesso opposto (vedi entity.findPartner), quindi dieci maschi sono gia' una
     * popolazione morta. Il numero che conta non e' quanti sono vivi ma quante COPPIE POSSIBILI
     * ci sono, cioe' il minimo tra maschi e femmine in eta' da riproduzione: se quel minimo e'
     * zero non nascera' piu' nessuno, per quanto cibo ci sia.
     *
     * I morti per sesso servono a distinguere due cause che portano allo stesso risultato: un
     * sesso che muore di piu' (e allora c'e' qualcosa da correggere nel modello), oppure la
     * semplice deriva della moneta lanciata a ogni nascita quando la popolazione e' piccola
     * (e allora non c'e' niente di rotto: e' la dimensione della popolazione il problema).
     */
    private static String sessi() {
        int maschi = 0, femmine = 0, maschiMaturi = 0, femmineMature = 0;
        for (entity e : Entity_manager.snapshotAlive()) {
            boolean f = e.getSex() == 2;
            if (f) {
                femmine++;
                if (e.isMature()) femmineMature++;
            } else {
                maschi++;
                if (e.isMature()) maschiMaturi++;
            }
        }
        int vivi = maschi + femmine;
        int[] morti = Simulation.mortiPerSesso();
        int mortiTot = morti[0] + morti[1];
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Vivi: %d maschi, %d femmine", maschi, femmine));
        if (vivi > 0) {
            sb.append(String.format(Locale.ROOT, "  (%.0f%% maschi)", 100.0 * maschi / vivi));
        }
        sb.append('\n');
        sb.append(String.format(Locale.ROOT, "In eta' da riproduzione: %d maschi, %d femmine%n",
                maschiMaturi, femmineMature));
        int coppie = Math.min(maschiMaturi, femmineMature);
        sb.append("Coppie possibili: ").append(coppie);
        if (coppie == 0) {
            sb.append("  <-- non puo' nascere piu' nessuno");
        }
        sb.append('\n');
        sb.append(String.format(Locale.ROOT, "Morti: %d maschi, %d femmine", morti[0], morti[1]));
        if (mortiTot > 0) {
            sb.append(String.format(Locale.ROOT, "  (%.0f%% maschi)", 100.0 * morti[0] / mortiTot));
        }
        return sb.toString();
    }

    /** I nomi delle azioni, nell'ordine degli indici ACT_* di entity. */
    private static final String[] NOMI_AZIONI = {
            "ferma", "avanti", "indietro", "gira a destra", "gira a sinistra",
            "testa a destra", "testa a sinistra", "mangia", "attacca", "dorme",
            "si riproduce", "beve"
    };

    /**
     * Quante volte e' stata scelta ciascuna azione.
     *
     * E' il conto piu' utile che ci sia per capire se il cervello sta facendo qualcosa o sta
     * oscillando: un'entita' che gira a destra e a sinistra lo stesso numero di volte, molte
     * volte, non sta cercando - sta vibrando (e' cosi' che si sono trovati i tre oscillatori
     * annotati in STATO_LAVORO.txt).
     */
    private static String azioni() {
        long[] conti = entity.actionCounts;
        long totale = 0;
        for (long c : conti) {
            totale += c;
        }
        StringBuilder sb = new StringBuilder("Azioni scelte: " + totale + " in tutto\n");
        for (int i = 0; i < conti.length; i++) {
            String nome = i < NOMI_AZIONI.length ? NOMI_AZIONI[i] : "azione " + i;
            sb.append(String.format(Locale.ROOT, "  %-16s %8d  (%.1f%%)%n",
                    nome, conti[i], totale == 0 ? 0 : 100.0 * conti[i] / totale));
        }
        return sb.toString();
    }

    private static String elencoVive(int quante) {
        List<entity> vive = new ArrayList<>(Entity_manager.snapshotAlive());
        vive.sort((x, y) -> Integer.compare(y.getAge(), x.getAge()));
        StringBuilder sb = new StringBuilder("Entita' vive: " + vive.size() + "\n");
        sb.append(String.format("  %-6s %-5s %-4s %-5s %-5s %-6s %-5s %s%n",
                "id", "gen", "eta'", "cibo", "acqua", "salute", "mele", "dieta"));
        for (int i = 0; i < Math.min(quante, vive.size()); i++) {
            entity e = vive.get(i);
            sb.append(String.format("  #%-5d %-5d %-4d %-5d %-5d %-6d %-5d %s%n",
                    e.getId(), e.getGenome().lineage().generation(), e.getAge(),
                    e.getFood(), e.getThirst(), e.getHealt(), e.getApplesEaten(),
                    e.dietNow()));
        }
        if (vive.size() > quante) {
            sb.append("  ... e altre ").append(vive.size() - quante);
        }
        return sb.toString();
    }

    private static String elencoMorte(int quante) {
        List<entity> tutte = new ArrayList<>(Entity_manager.snapshotAll());
        tutte.removeIf(entity::isAlive);
        tutte.sort((x, y) -> Long.compare(y.getDeathTick(), x.getDeathTick()));
        StringBuilder sb = new StringBuilder("Entita' morte: " + tutte.size() + "\n");
        sb.append(String.format("  %-6s %-5s %-6s %-7s %s%n", "id", "gen", "eta'", "tick", "causa"));
        for (int i = 0; i < Math.min(quante, tutte.size()); i++) {
            entity e = tutte.get(i);
            sb.append(String.format("  #%-5d %-5d %-6d %-7d %s%n",
                    e.getId(), e.getGenome().lineage().generation(), e.getAge(),
                    e.getDeathTick(), e.getDeathCause()));
        }
        if (tutte.size() > quante) {
            sb.append("  ... e altre ").append(tutte.size() - quante);
        }
        return sb.toString();
    }

    private static String elencoSalvataggi() {
        java.io.File[] dirs = new java.io.File("saving/").listFiles(java.io.File::isDirectory);
        if (dirs == null || dirs.length == 0) {
            return "Nessun salvataggio in saving/";
        }
        StringBuilder sb = new StringBuilder("Salvataggi disponibili (load <n>):\n");
        for (int i = 0; i < dirs.length; i++) {
            sb.append("  ").append(i).append(" - ").append(dirs[i].getName()).append('\n');
        }
        return sb.toString();
    }
}
