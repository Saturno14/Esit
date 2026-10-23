package src.body;

import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.CapsuleShape;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EMotionType;

/**
 * Prova minima che la fisica Jolt funziona: una capsula lasciata cadere su un pavimento si
 * ferma appoggiata a terra. Serve solo a verificare nativi, strati di collisione e passo di
 * integrazione; i corpi articolati sono in {@link JoltRagdoll}.
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
            System.exit(ok ? 0 : 1);
        }
    }
}
