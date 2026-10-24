# Da fare

Aggiornato il 24/10/2026. Le voci chiuse stanno in STATO_LAVORO.txt, non qui.

## Corpo e locomozione

- [ ] **Equilibrio laterale dei bipedi.** E' quello che resta aperto dopo il lavoro del
      23/10/2026 (voce 9 del diario): con i motori sistemati un corpo tiene la posa eretta per
      circa un secondo e poi cede DI LATO. Il riflesso di Balance corregge beccheggio e rollio ma
      non basta: per stare su davvero serve un controllore di PASSO (capture point - si cade e si
      mette un piede avanti), che e' un progetto a se'. Con 40 prove per corpo le cadute sono
      scese da 75/78 a 42/78, quindi la strada e' aperta anche solo dando piu' tempo alla ricerca.
- [ ] **Quanto costa il budget di prove.** 24 prove per fondatrice e 8 per figlio portano una
      corsa di Experiment da 30 a 74 secondi. Con 40 prove i corpi camminano molto meglio
      (+0.758 contro +0.308 m/s di mediana) ma il costo raddoppia ancora. Non e' stato cercato
      l'optimum, ne' provata una ricerca piu' furba (CMA-ES, o riusare l'andatura di un parente
      con corpo simile).
- [x] ~~Serpente che non avanza~~ - ERA UNA FALSA PISTA. Quel serpente era scritto a mano
      (BodyPlan.snake). I corpi che strisciano davvero, quando l'evoluzione li produce (5.3% dopo
      60 generazioni, ZERO dopo 12), AVANZANO: mediana +0.110 m/s, massima +0.585, non -0.039. Il
      confronto a mano in GaitEvaluator.main ora tiene solo esperimenti controllati, e i corpi
      rappresentativi li da' src.body.EvolvedBodies.
- [ ] **Quadrupedi: non cadono e vanno molto piu' veloci** della mediana dei bipedi, ma sono il
      2.6% dei corpi. Da guardare se la selezione nella simulazione li favorisce, e perche' sono
      cosi' rari.
- [ ] **currentAngles() legge solo le cerniere** e lascia zero i giunti a due o tre gradi di
      liberta' (Jolt non espone l'angolo per asse). Ora e' documentato nel codice, ma un modo
      vero di leggerli manca, e la mancanza mi e' costata mezza giornata di caccia al difetto
      sbagliato.
- [ ] **Giunti a 2-3 gradi di liberta' nella fisica.** In Jolt diventano un solo
      SixDofConstraint con assi ortogonalizzati (Gram-Schmidt): e' un'approssimazione, la catena
      di cerniere esatta resta solo nell'esportazione URDF. Da verificare se sposta le misure.
- [ ] **Fisica sempre viva, invece che come oracolo.** Misurato: 380 corpi con tutti i giunti
      comandati costano 1.93 ms per passo (116 ms per tick di mondo contro i 10 disponibili a
      x50): si potrebbe fare solo fino a circa x4. Da riprendere se si accetta quel tetto.
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
- [ ] **Il palco (fisica viva) resta spento**: con il controllore di oggi quattro entita' in
      fisica vera camminano a ~0.05 m/s contro 0.27 di riferimento, con 339 cadute in 600 tick.
      Serve prima il controllore di passo (prima voce di questo file). Poi: terreno vero nel
      mondo fisico invece del pavimento piatto, e rialzarsi da soli.

## Pulizia

- [x] ~~`Entity_manager.getRandomPartner()` e' codice morto~~ - rimosso con le altre zone morte
      (voce 18).
- [ ] **`src.body.BodyPlanTest` e' rosso da prima**: 10 verifiche su 2421 ("JSON stabile gen N").
      Verificato sui binari di HEAD: non e' una rottura nuova, e' un test che nessuno guardava.
- [ ] `EntityManagerBridge.sync()` itera per indice su una lista che cambia sotto: oggi se la cava
      con un try/catch, meglio uno snapshot.
- [ ] Costi per frame e per tick: scansioni dell'intero mondo per le mele e dei cadaveri per
      ogni entita' affamata.
