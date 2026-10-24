# Da fare

Aggiornato il 25/10/2026. Le voci chiuse stanno in STATO_LAVORO.txt, non qui.

## Corpo e locomozione

- [x] **LA FASE DECISA CON L'UTENTE IL 25/10/2026: un mondo fisico, autonomo e non
      controllato.** TUTTI E CINQUE I PASSI FATTI (voci 25 e 26). Quello che resta sono le misure
      da chiudere, non il lavoro: le voci qui sotto.
      - [x] 1. Girare e' un'abilita' scoperta dal corpo (voce 25).
      - [x] 2. Il terreno vero e l'acqua nella fisica (voci 25 e 26).
      - [x] 3. Un solo mondo: `Palco` cancellato, `MondoFisico` collegato (voce 26).
      - [x] 4. Via il tetto di popolazione: resta `Simulation.limiteDiSicurezza` (voce 26).
      - [x] 5. Via ogni premio tranne quello della riproduzione (voce 26).

- [ ] **CHE COSA IMPARA UN'ENTITA' SENZA PREMI DENSI.** E' la domanda aperta piu' grossa che il
      passo 5 lascia, ed e' prevista, non scoperta: l'apprendimento in vita tiene una mutazione
      del cervello solo se la finestra ha fatto MEGLIO, e senza premi quasi tutte le finestre
      valgono zero - quindi quasi tutte le mutazioni vengono annullate e il cervello resta quello
      con cui si e' nati. L'apprendimento si sposta sull'evoluzione fra generazioni.
      Da guardare, in ordine: (a) quante mutazioni vengono tenute per vita, adesso contro
      `premi=1`; (b) se la finestra ha ancora senso come meccanismo o se va sostituita da
      qualcosa che guarda il TEMPO VISSUTO invece del punteggio (sopravvivere e' il segnale che
      resta); (c) se la riproduzione da sola, che e' sparsa ma non nulla, basta a muovere il
      cervello in una direzione.
      Il braccio di controllo esiste e costa un flag: `premi=1` in SimRunner.
      PRIMA MISURA (3 semi, 3000 tick, appaiati): senza premi 166 nascite e 35 vivi, coi premi di
      prima 146 e 31 - due semi su tre a favore di "senza premi", p = 1.0, cioe' tre semi non
      dichiarano niente MA la previsione qui sopra non si vede. Due letture, nessuna provata: o
      l'apprendimento in vita contava poco anche prima, o la riproduzione da sola basta. La prima
      e' da verificare per prima, perche' sarebbe apparato che non paga. Da chiudere a 12 semi.

- [ ] **LA POPOLAZIONE CROLLA, E QUESTO E' IL PROBLEMA NUMERO UNO** (misurato, voce 26 sezione 7).
      Una corsa da 9000 tick: 68 fondatrici, massimo 86 entita' intorno al tick 600, ultima
      nascita al tick 2000, due superstiti dal 4500, uno alla fine - con 248 MELE SUL TERRENO e
      86 morti di fame. Il tetto di popolazione non c'entrava niente (era 375 e non e' mai stato
      sfiorato: toglierlo non ha cambiato nessun numero, e la competizione per il cibo NON e'
      comparsa).
      LA CAUSA NON E' ECOLOGICA, E' IL MOVIMENTO: un corpo che all'oracolo fa 0.2+ m/s nel mondo
      ne fa 0.14, e in quella corsa ci sono 7163 collassi - un corpo per terra non raggiunge
      quello che vede, e nessuno lo rialza (voce 24, ed e' giusto cosi'). Da affrontare in
      quest'ordine, perche' sono la stessa catena: (1) rialzarsi da terra come abilita' cercata
      (la voce dedicata qui sotto), (2) il terreno a gradini da un metro (voce qui sopra), (3) le
      andature che spiraleggiano invece di andare dritte (voce 25).
      Un numero che conferma dal lato opposto: la sete era il 37.5% delle morti (voce 19), ora e'
      il 5-21% - non perche' l'acqua sia migliorata, perche' si muore di fame prima.

- [ ] **IL TERRENO E' UNA SCALINATA DI GRADINI DA UN METRO.** `world.surfaceY` e' un INDICE di
      cella, cioe' un intero: il campo di altezze che la fisica riceve ha gradini verticali da un
      metro esatto dove il terreno cambia quota. Un corpo alto 40 cm non ci sale. E' fedele a
      quello che il motore grafico DISEGNA (cubi), quindi non e' un difetto da correggere di
      nascosto: e' una scelta, e l'alternativa (mediare le quote delle quattro celle attorno a
      ogni nodo, cioe' rampe a 45 gradi) renderebbe il terreno fisico diverso da quello disegnato
      di mezzo metro. Da decidere con l'utente, non da sistemare. Il numero da portare alla
      decisione: nel mondo un corpo che all'oracolo fa 0.2+ m/s copre 1.43 m in dieci secondi,
      cioe' 0.14 m/s.

- [ ] **GLI INCONTRI SONO ANCORA NELLA GRIGLIA.** La fisica muove i corpi e i corpi si urtano
      (un gruppo di collisione per entita', quindi due entita' che si incontrano si incontrano
      davvero), ma mangiare, bere, attaccare e accoppiarsi guardano ancora le celle e le portate.
      Non e' urgente e non e' una contraddizione - e' il prossimo confine fra il mondo fisico e
      il mondo a griglia.

- [ ] **LA RICERCA, NON IL CONTROLLO: il riflesso ha 350 pesi e la salita della collina non li
      sa cercare.** E' quello che resta aperto dopo il rifacimento del movimento (voce 21).
      L'apparato c'e' tutto - sensi per giunto, contatto per segmento, pesi ereditati e allenati -
      e A PARI PROVE non paga: 78 corpi appaiati, riflesso contro solo oscillatore a 48 prove,
      velocita' pari (p = 0.73) e cadute peggiori (12 contro 2, p = 0.013). Quello che sembrava
      un guadagno era il budget: l'oscillatore da solo, da 24 a 48 prove, va da +0.717 a +0.903
      con p = 0.0001. Il segno che e' la ricerca e non i sensi: il carico sui quattordici canali
      resta quasi uniforme (7-10%) invece di concentrarsi. Da provare, in ordine: le tre tarature
      gia' pronte (`-Driflesso.sigma`, `.tetto`, `.frazione`, `.quota`), poi un passo adattivo per
      peso o CMA-ES, poi molte piu' prove durante la vita (costa calcolo, non cibo - e lo
      svezzamento della voce 20 e' gia' lì per questo).
      NIENTE CONTROLLORI DI PASSO scritti a mano: vietato dall'utente il 24/10/2026, e con ragione
      (presuppongono piedi e poligono di appoggio, quindi spostano la selezione verso le
      morfologie che il controllore sa gestire). `Balance` e' stato cancellato e non e' costato
      niente: con Balance +0.638 m/s e 57 cadute, a ciclo aperto +0.717 e 59.
- [ ] **Il braccio autonomo e' la cosa da far crescere.** Con `oscillatore=0` l'oscillatore e'
      solo un orologio e il movimento lo generano i pesi: +0.331 m/s, 61 cadute, pari
      all'oscillatore con mezzo budget (p = 0.91). Senza nessuna forma d'onda data da fuori i
      corpi si muovono davvero. Da misurare di nuovo appena la ricerca migliora.
- [ ] **"I rami divergono?" e' ancora senza risposta.** L'intravisto (quadrupedi carichi su
      "contatto opposto" e "vel.avanti", bipedi sul beccheggio) e' dentro il rumore: 4 quadrupedi
      su 78 corpi e l'ordine dei canali cambia da un braccio all'altro. Serve piu' campione per
      forma - e un riflesso che abbia davvero imparato qualcosa.
- [ ] **Quanto costa il budget di prove.** 24 prove per fondatrice e 8 per figlio portano una
      corsa di Experiment da 30 a 74 secondi. Con 40 prove i corpi camminano molto meglio
      (+0.758 contro +0.308 m/s di mediana) ma il costo raddoppia ancora. Non e' stato cercato
      l'optimum, ne' provata una ricerca piu' furba (CMA-ES, o riusare l'andatura di un parente
      con corpo simile). CONFERMATO E QUANTIFICATO il 24/10/2026 (voce 21): sugli stessi 78 corpi
      appaiati, da 24 a 48 prove la mediana va da +0.717 a +0.903 m/s e le cadute da 59 a 45, con
      57 corpi su 78 a favore, p = 0.0001. Il budget di prove e' oggi la leva piu' forte che
      questo progetto abbia sul movimento - piu' forte di qualunque cambio di controllore
      provato finora. Riusare l'andatura di un parente e' in parte fatto (GaitArchive, voce 20).
- [x] ~~Serpente che non avanza~~ - ERA UNA FALSA PISTA. Quel serpente era scritto a mano
      (BodyPlan.snake). I corpi che strisciano davvero, quando l'evoluzione li produce (5.3% dopo
      60 generazioni, ZERO dopo 12), AVANZANO: mediana +0.110 m/s, massima +0.585, non -0.039. Il
      confronto a mano in GaitEvaluator.main ora tiene solo esperimenti controllati, e i corpi
      rappresentativi li da' src.body.EvolvedBodies.
- [ ] **Quadrupedi: non cadono e vanno molto piu' veloci** della mediana dei bipedi, ma sono il
      2.6% dei corpi. Da guardare se la selezione nella simulazione li favorisce, e perche' sono
      cosi' rari.
- [x] ~~currentAngles() legge solo le cerniere~~ - RISOLTO nella voce 21:
      `JoltRagdoll.jointAngles` legge l'angolo di tutti i giunti, anche quelli a due e tre gradi
      di liberta', dalla rotazione relativa proiettata sull'asse. Serviva per dare i sensi al
      riflesso, e senza di esso erano ciechi proprio sui giunti piu' importanti.
- [ ] **Giunti a 2-3 gradi di liberta' nella fisica.** In Jolt diventano un solo
      SixDofConstraint con assi ortogonalizzati (Gram-Schmidt): e' un'approssimazione, la catena
      di cerniere esatta resta solo nell'esportazione URDF. Da verificare se sposta le misure.
- [x] ~~**Fisica sempre viva, invece che come oracolo.**~~ FATTO (voci 22 e 23). Il tetto che la
      teneva spenta era un errore di contabilita': 10 ms sono il budget di un tick A x50, ma un
      tick vale mezzo secondo di mondo. Il palco misura il costo per corpo tick per tick e ci
      mette dentro quante entita' ci stanno nel budget; a x1 ci sta il mondo intero. E la posa
      disegnata E' la posa fisica, non una ricreazione.
- [ ] **Budget di prove per corpo.** Ora: 8 prove per una fondatrice, 4 per un figlio
      (Locomotion.trialsPerBody / trialsPerChild). Con 0 prove per i figli la popolazione non
      impara a camminare (mediana 0.03 m/s); con 4 arriva a 0.27. Non e' stato cercato l'optimum.

## Grafica

- [ ] **Chiamate di disegno.** Ogni entita' costa 2 chiamate per segmento (36 per un umanoide):
      con 250 vivi sono ~9000 chiamate per frame. Il passo successivo e' il disegno a istanze
      (una chiamata per tutte le sfere, una per tutti i cilindri) o il taglio per distanza.
- [ ] **Non ancora visto girare in OpenGL** il disegno per segmenti: compila e la matematica
      della posa e' verificata (piedi a y=0 su tutto il ciclo), ma la finestra non e' stata
      aperta su questa macchina.
- [ ] **L'acqua non viene mai disegnata** (world.isWaterColumn esiste, il mesher la ignora).
      Trovato leggendo il codice, non ancora affrontato.

## Bilanciamento

- [ ] **embodied contro structured.** Vedi l'ultima voce di STATO_LAVORO.txt per i numeri.
- [ ] **Attacchi liberi**: a 15000 tick portavano all'estinzione 4 seed su 4 tra quelli bassi
      (voce del 03/10/2026). Non ancora ricontrollato con il corpo che conta.
- [ ] **L'ACQUA E' IL COLLO DI BOTTIGLIA.** Contato su 24 corse da 6000 tick (voce 19): la sete
      e' il **37.5%** delle morti (1511 su 4033), contro 56.7% di fame e 5.8% di combattimento,
      e per corsa va dal 26% al 52%. Il vecchio "la sete e' il 3% delle morti" era scritto sopra
      il conteggio sbagliato di Simulation.kill (voce 18): quel 3% non e' mai esistito. Da
      affrontare: `drink()` a raggio di una cella che non usa la portata delle braccia, e
      l'acqua che non viene mai disegnata (quindi non si vede nemmeno dove sia il problema).
- [ ] **La richiesta di accoppiamento dimezza le nascite** (voce 19): 12 semi appaiati, nascite
      2079 -> 976 e vivi 440 -> 94, p = 0.006. Manca il primo pezzo del protocollo deciso: ci si
      AVVICINA, poi si chiede. Interruttore `ActionWeights.n_moveMate` pronto e spento: da
      misurare. Se non basta: allungare RICHIESTA_TTL, esentare il partner da maskSeen, o
      allargare PARTNER_RANGE.
- [x] ~~Svezzamento da chiudere~~ - CHIUSO a 24 semi: nascite +56% (1736 -> 2716), 19 semi su 24,
      p = 0.0066. Acceso a 0.75 in `embodied()`.
- [ ] **Tarare lo svezzamento**: 0.75 e la soglia di meta' `REFERENCE_SPEED` sono scelti a occhio,
      e la popolazione finale triplica senza passare il test dei segni (p = 0.093). In piu' le
      corse estinte sono 3 contro 1: piu' nascite non vuol dire meno estinzioni, e il perche'
      (popolazione piu' grande = consumo piu' grande) va guardato.
- [x] ~~Il palco (fisica viva) resta spento~~ - ACCESO il 24/10/2026 (voce 22), e dal 25/10 il
      palco NON ESISTE PIU': c'e' un mondo solo (voce 26). Tutte le entita'
      che stanno nel budget di tempo del tick camminano in fisica vera, e la posa disegnata sono
      gli angoli veri dei giunti. Costa: 6 semi appaiati, nascite 625 -> 509 (-19%), vivi
      242 -> 152 (-37%), cinque semi su sei peggio, p = 0.22 (da chiudere a 12 semi). La velocita'
      vera e' 0.15-0.29 m/s contro gli 0.27 che la formula regalava a ogni tick: non e' una
      penalita' della fisica, e' la fine di un sussidio.
- [ ] **Rimisurare la fisica viva a 12 semi, da capo.** I sei semi della voce 22 sono SCADUTI:
      quel braccio contava i metri anche a chi cadeva in avanti scivolando, cioe' pagava le
      cadute (voce 23). Sul codice di adesso, 3 semi x 3000 tick:
          senza fisica                  300 nascite  119 vivi   (formula: 0.27 m/s regalati)
          credito solo a chi cammina    107 nascite   22 vivi   0.021-0.046 m/s veri
          chi e' sveglio si rialza      168 nascite   44 vivi   0.043-0.053 m/s veri
      Tre semi non dichiarano niente e il confronto interessante non e' piu' "fisica si/no" ma
      "quanto manca ai 300". Da chiudere a 12 semi, e da rifare il seme 4 (l'unico in cui la
      fisica viva aveva fatto meglio) ora che il credito e' onesto.
      AGGIORNATO il 25/10/2026, senza il rimontaggio (voce 24): 105 nascite e 29 vivi, m/s veri
      0.020-0.032. La domanda per il lotto a dodici e' diventata piu' interessante di "quanto si
      perde": il seme 2 e' andato meglio SENZA l'aiuto e ha raggiunto la generazione 7, i semi 1 e
      3 si sono fermati alla 3. Se e' il caso o se togliere il paracadute seleziona meglio, tre
      semi non lo dicono.
- [ ] **RIALZARSI DA TERRA: ora e' un'abilita' da imparare, non una concessione.** Il
      rimontaggio dal mondo e' stato CANCELLATO il 25/10/2026 su richiesta dell'utente (voce 24):
      chi cade resta caduto, e progredisce solo chi arriva al cibo anche da terra. Costo misurato:
      168 -> 105 nascite, 44 -> 29 vivi su tre semi, velocita' vera dimezzata. Nessun seme si e'
      estinto, e il seme 2 e' andato MEGLIO senza l'aiuto (53 -> 61 nascite, 3 -> 18 vive,
      generazione 7 contro il 5 di tutti gli altri bracci).
      Quello che manca e' il compito: chi e' per terra non ha nessun motivo per muoversi in un
      modo invece che in un altro, perche' il punteggio guarda solo l'avanzare. Un termine che
      premi il TORNARE alti senza premiare lo STARE alti sarebbe di nuovo UPRIGHT_BONUS (l'errore
      della voce 22): la strada giusta e' probabilmente un SECONDO COMPITO - prove che partono
      da terra, punteggio separato - invece di un altro addendo nello stesso punteggio.
      NON RIMETTERE IL RIMONTAGGIO: MondoFisicoSelfTest ha le verifiche che lo vietano, e il
      motivo e' nella voce 24 (una pezza che aiuta i corpi scadenti decide da fuori chi ha diritto
      di funzionare, come Balance e come UPRIGHT_BONUS).
      NOTA DALLA VOCE 26: nel mondo unico "chi e' a terra non avanza" NON e' piu' vero - un corpo
      crollato scivola di 1.6 m in venti secondi, perche' il filtro che dava i metri solo a chi
      era in piedi era un giudizio sulla postura ed e' andato via col palco. Resta GIU', e questo
      si verifica.
- [x] ~~**Terreno vero nel mondo fisico.**~~ FATTO (voci 25 e 26): il terreno della simulazione
      e' un campo di altezze in Jolt, i tronchi sono cilindri statici, l'acqua delle colonne
      allagate ha spinta e resistenza. Quello che resta e' nella voce "il terreno e' una scalinata"
      qui sopra, e negli incontri che vivono ancora nella griglia.
- [ ] **Giunti a tre assi: l'angolo misurato sfora i limiti** fino a 1.8 rad (mai sulle cerniere -
      vedi voce 22). Oggi e' tamponato dove conta (il disegno taglia sui limiti, il canale
      dell'angolo e' tagliato a +-2), ma la decomposizione esatta di un giunto a tre assi in Jolt
      resta da fare, e finche' non c'e' il riflesso legge un angolo approssimato.

## Pulizia

- [x] ~~`Entity_manager.getRandomPartner()` e' codice morto~~ - rimosso con le altre zone morte
      (voce 18).
- [ ] **`src.body.BodyPlanTest` e' rosso da prima**: 10 verifiche su 2421 ("JSON stabile gen N").
      Verificato sui binari di HEAD: non e' una rottura nuova, e' un test che nessuno guardava.
- [ ] `EntityManagerBridge.sync()` itera per indice su una lista che cambia sotto: oggi se la cava
      con un try/catch, meglio uno snapshot.
- [ ] Costi per frame e per tick: scansioni dell'intero mondo per le mele e dei cadaveri per
      ogni entita' affamata.
