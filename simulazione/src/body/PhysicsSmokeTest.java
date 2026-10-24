package src.body;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.CapsuleShape;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;

/**
 * Prova minima che la fisica Jolt funziona: una capsula lasciata cadere su un pavimento si
 * ferma appoggiata a terra, e una lasciata cadere su un TERRENO si ferma alla quota di quel
 * terreno e scivola giu' da una salita. Serve a verificare nativi, strati di collisione, passo di
 * integrazione e campo di altezze; i corpi articolati sono in {@link JoltRagdoll}.
 *
 *   java -cp "<classpath>" src.body.PhysicsSmokeTest
 * (il classpath lo scrive mvn: vedi STATO_LAVORO.txt)
 */
public final class PhysicsSmokeTest {

    /** Compenetrazione ammessa da Jolt a riposo (PhysicsSettings.mPenetrationSlop). */
    public static final double PENETRATION_SLOP = 0.02;

    public static void main(String[] args) {
        PhysicsNatives.require();
        System.out.println("Jolt " + PhysicsNatives.version());

        try (PhysicsWorld w = new PhysicsWorld(64, 2)) {
            w.addGround(50f, 0f, 0.8f);

            float radius = 0.1f, halfHeight = 0.3f;
            CapsuleShape shape = new CapsuleShape(halfHeight, radius);
            BodyCreationSettings bcs = new BodyCreationSettings(shape,
                    new RVec3(0, 3, 0), new Quat(0, 0, 0, 1), EMotionType.Dynamic, PhysicsWorld.LAYER_BODY);
            int id = w.bodies().createAndAddBody(bcs, EActivation.Activate);
            w.optimize();

            // Jolt ammette una piccola compenetrazione a riposo (penetration slop, 0.02 m per
            // impostazione predefinita): un corpo appoggiato sta a (raggio + mezza altezza) - slop.
            double expected = halfHeight + radius - PENETRATION_SLOP;
            for (int i = 1; i <= 600; i++) {         // 5 secondi a 120 Hz
                w.step();
                if (i % 120 == 0) {
                    RVec3 p = w.bodies().getPosition(id);
                    System.out.printf(java.util.Locale.ROOT, "t=%.1fs  y=%.4f  corpi=%d attivi=%d%n",
                            i * PhysicsWorld.TIME_STEP, p.y(), w.bodyCount(), w.activeBodyCount());
                }
            }
            double y = w.bodies().getPosition(id).y();
            boolean ok = Math.abs(y - expected) < 0.005;
            System.out.println(ok
                    ? "OK: la capsula si e' fermata a y=" + String.format(java.util.Locale.ROOT, "%.4f", y)
                            + " (attesa " + String.format(java.util.Locale.ROOT, "%.4f", expected) + ")"
                    : "FALLITO: y=" + y + ", attesa " + expected);
            if (!ok) {
                System.exit(1);
            }
        }

        System.exit(terreno() && acqua() ? 0 : 1);
    }

    /**
     * L'ACQUA. Una capsula lasciata cadere da tre metri dentro una conca allagata, e la stessa
     * capsula a secco: in acqua deve arrivare al fondo PIU' LENTAMENTE e fermarsi prima di
     * rimbalzare. Non si verifica che galleggi - i corpi di questo progetto hanno la densita'
     * dell'acqua (Automaton.DENSITY = 1000), quindi NON galleggiano e non affondano: restano
     * dove sono, ed e' una conseguenza della densita' che c'era gia', non un numero scelto qui.
     * Quello che si verifica e' che l'acqua FRENI: se un corpo cade in acqua come nel vuoto,
     * l'acqua non c'e'.
     */
    private static boolean acqua() {
        float radius = 0.1f, halfHeight = 0.3f;
        CapsuleShape shape = new CapsuleShape(halfHeight, radius);
        double[] quote = new double[2];
        for (int caso = 0; caso < 2; caso++) {
            boolean inAcqua = caso == 1;
            try (PhysicsWorld w = new PhysicsWorld(64, 2)) {
                w.addGround(50f, 0f, 0.8f);
                int id = w.bodies().createAndAddBody(new BodyCreationSettings(shape,
                        new RVec3(0, 3, 0), new Quat(0, 0, 0, 1), EMotionType.Dynamic,
                        PhysicsWorld.LAYER_BODY), EActivation.Activate);
                w.optimize();
                // mezzo secondo: in caduta libera da 3 m si arriva a terra in ~0.74 s
                for (int i = 0; i < 60; i++) {
                    if (inAcqua) {
                        w.galleggia(id, 3.0, PhysicsWorld.TIME_STEP);
                    }
                    w.step();
                }
                quote[caso] = w.bodies().getPosition(id).y();
            }
        }
        boolean ok = quote[1] > quote[0] + 0.05;
        System.out.printf(java.util.Locale.ROOT,
                "acqua: dopo mezzo secondo di caduta, a secco y=%.3f, in acqua y=%.3f%n",
                quote[0], quote[1]);
        System.out.println(ok ? "OK: l'acqua frena la caduta"
                : "FALLITO: in acqua il corpo cade come nel vuoto");
        return ok;
    }

    /**
     * IL TERRENO. Un campo di altezze a gradini: piatto a quota 2 per meta' e a quota 5 per
     * l'altra, con una rampa in mezzo. Due cose da verificare, e sono le due che il mondo chiede:
     *  - una capsula lasciata cadere sul pianoro basso si ferma ALLA QUOTA DEL TERRENO, non a
     *    zero: cioe' il terreno c'e' davvero e non e' un pavimento piatto mascherato;
     *  - una capsula lasciata cadere sulla rampa NON CI RESTA: scivola verso il basso. E' la
     *    verifica che una pendenza e' una pendenza - se un corpo ci si appoggiasse sopra come su
     *    un tavolo, "provare a superare un dosso" non vorrebbe dire niente.
     */
    private static boolean terreno() {
        int celle = 20;
        int lato = PhysicsWorld.latoCampioni(celle);
        float[] h = new float[lato * lato];
        for (int ix = 0; ix < lato; ix++) {
            for (int iz = 0; iz < lato; iz++) {
                // basso fino a x=6, rampa da 6 a 12, alto oltre: una collina di tre metri
                // la salita va verso x grandi: serve a verificare anche CHE ASSE e' quale
                float q = ix <= 6 ? 2f : ix >= 12 ? 5f : 2f + 3f * (ix - 6) / 6f;
                h[PhysicsWorld.indiceCampione(ix, iz, lato)] = q;
            }
        }
        try (PhysicsWorld w = new PhysicsWorld(64, 2)) {
            w.addHeightField(h, lato, 1f, 0.8f);
            float radius = 0.1f, halfHeight = 0.3f;
            CapsuleShape shape = new CapsuleShape(halfHeight, radius);
            int piano = w.bodies().createAndAddBody(new BodyCreationSettings(shape,
                    new RVec3(3, 6, 3), new Quat(0, 0, 0, 1), EMotionType.Dynamic,
                    PhysicsWorld.LAYER_BODY), EActivation.Activate);
            int rampa = w.bodies().createAndAddBody(new BodyCreationSettings(shape,
                    new RVec3(9, 8, 9), new Quat(0, 0, 0, 1), EMotionType.Dynamic,
                    PhysicsWorld.LAYER_BODY), EActivation.Activate);
            w.optimize();
            double rampaX0 = w.bodies().getPosition(rampa).x();
            double rampaZ0 = w.bodies().getPosition(rampa).z();
            for (int i = 0; i < 600; i++) {
                w.step();
            }
            RVec3 pPiano = w.bodies().getPosition(piano);
            RVec3 pRampa = w.bodies().getPosition(rampa);
            double attesa = 2 + halfHeight + radius - PENETRATION_SLOP;
            boolean quotaOk = Math.abs(pPiano.y() - attesa) < 0.05;
            // scivola verso il basso, cioe' verso x piccole (la salita va verso x grandi)
            boolean scivolaOk = pRampa.x() < rampaX0 - 0.2;
            /*
             * E CHE SIA SCIVOLATA LUNGO LA X, non lungo la Z. Non e' pignoleria: se i campioni
             * fossero indicizzati al contrario il terreno sarebbe TRASPOSTO, la capsula
             * scivolerebbe lungo Z, e nel mondo le colline starebbero dove la simulazione vede le
             * valli senza che nessun errore lo dica. E' cosi' che ho scoperto l'ordine giusto.
             */
            boolean assiOk = Math.abs(pRampa.z() - rampaZ0) < Math.abs(pRampa.x() - rampaX0);
            System.out.printf(java.util.Locale.ROOT,
                    "terreno: sul pianoro y=%.3f (attesa %.3f), sulla rampa x da %.2f a %.2f, "
                            + "z da %.2f a %.2f (y=%.2f)%n",
                    pPiano.y(), attesa, rampaX0, pRampa.x(), rampaZ0, pRampa.z(), pRampa.y());
            if (!assiOk) {
                System.out.println("FALLITO: la capsula scivola lungo Z e la salita e' lungo X: "
                        + "i campioni del terreno sono indicizzati al contrario");
            }
            if (!quotaOk) {
                System.out.println("FALLITO: sul terreno la capsula non si ferma alla quota del "
                        + "terreno");
            }
            if (!scivolaOk) {
                System.out.println("FALLITO: sulla rampa la capsula non scivola giu'");
            }
            if (quotaOk && scivolaOk && assiOk) {
                System.out.println("OK: il terreno ha quote e pendenze vere, e gli assi sono "
                        + "quelli giusti");
            }
            return quotaOk && scivolaOk && assiOk;
        }
    }
}
