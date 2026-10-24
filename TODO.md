# Da fare

Aggiornato il 24/10/2026. Le voci chiuse stanno in STATO_LAVORO.txt, non qui.

## Corpo e locomozione

- [ ] **LA FASE IN CORSO (decisa con l'utente il 25/10/2026): un mondo fisico, autonomo e non
      controllato.** Cinque passi, in ordine. 1 FATTO (la svolta, voce 25), 2 a META' (i mattoni
      del terreno e dell'acqua ci sono e sono provati; manca metterli nel mondo), 3-4-5 da fare.
      - [x] **1. Girare e' un'abilita' scoperta dal corpo** (voce 25). 73 corpi su 78 sbagliano
            meno con la svolta trovata che senza, guadagno mediano 2.32 rad. Nel mondo: -18%
            nascite su 3 semi, +20% di orologio. Da chiudere a 12 semi.
      - [~] **2. Terreno e acqua nella fisica.** `PhysicsWorld.addHeightField`,
            `addStaticCylinder` (i tronchi) e `galleggia` (spinta e resistenza dell'acqua) sono
            scritti e verificati dal PhysicsSmokeTest. Da usare: ci vuole il passo 3, perche' con
            le corsie del palco un terreno non vuol dire niente.
      - [ ] **3. Un solo mondo.** `src.body.MondoFisico` E' SCRITTO MA NON COLLEGATO: compila e
            nessuno lo chiama ancora. Quello che resta da fare e' la parte che tocca tutto il
            resto: `entity.walk` non deve piu' spostare l'entita' (solo segnare l'intenzione e
            pagare la fatica), `syncCell` deve LEGGERE la posizione dal corpo
            (`MondoFisico.posizioneDi`), `facing` deve venire da `yawRoot`, e vanno via i rifiuti
            scritti a mano in `walk` (l'acqua non si attraversa, un albero non si attraversa):
            adesso l'acqua ha la sua fisica e un tronco e' un cilindro statico. Poi si cancella
            `Palco` e il suo credito di metri e radianti.
      - [ ] **4. Via il tetto di popolazione** (`Simulation.maxPopulation`, 375 a 50x50, gate in
            4 punti di entity.java). Il limite diventa il cibo. Conseguenza da misurare, non da
            nascondere: il costo della fisica non ha piu' un tetto.
      - [ ] **5. Via ogni premio tranne quello della riproduzione.** Vedi la voce dedicata qui
            sotto.

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
- [x] ~~Il palco (fisica viva) resta spento~~ - ACCESO il 24/10/2026 (voce 22). Tutte le entita'
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
      NON RIMETTERE IL RIMONTAGGIO: PalcoSelfTest ha tre verifiche che lo vietano, e il motivo e'
      nella voce 24 (una pezza che aiuta i corpi scadenti decide da fuori chi ha diritto di
      funzionare, come Balance e come UPRIGHT_BONUS).
- [ ] **Terreno vero nel mondo fisico.** Il pavimento del palco e' PIATTO, quindi la fisica dice
      come un corpo cammina, non come affronta un gradino. E gli urti fra entita' (il
      combattimento) e le mele restano nella simulazione a griglia: la fisica muove le gambe, non
      risolve gli incontri.
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
