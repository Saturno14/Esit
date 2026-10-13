package src.body;

import src.genetics.Genome;
import src.genetics.GenomeSchema;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Verifiche del modello del corpo (nessun framework di test nel progetto: si lancia come un main).
 *   java -cp "target\classes;<json.jar>" src.body.BodySelfTest
 * Esce con codice 1 se qualcosa fallisce.
 */
public final class BodySelfTest {

    private static int failures = 0, checks = 0;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FALLITO: " + what);
        }
    }

    private static boolean near(double a, double b, double tol) { return Math.abs(a - b) <= tol; }

    public static void main(String[] args) throws Exception {
        GenomeSchema schema = GenomeSchema.loadFromClasspath("/gene_schema.json");
        Automaton a = Automaton.humanoid(BodyParams.midpoint(schema));

        // struttura
        check(a.dofCount() == 25, "25 gradi di liberta' (trovati " + a.dofCount() + ")");
        check(a.links().size() == 18, "18 link (trovati " + a.links().size() + ")");
        check(a.joints().size() == 17, "17 giunti anatomici (trovati " + a.joints().size() + ")");
        check(a.jointOf("head").isFixed() && a.jointOf("hand_L").isFixed(), "testa e mani sono fisse");
        Set<String> names = new HashSet<>();
        for (Dof d : a.dofs()) {
            check(names.add(d.name), "nome gdl unico: " + d.name);
            check(d.lower <= 0 && d.upper >= 0 || d.name.startsWith("knee") || d.name.startsWith("elbow"),
                    "posa di riposo dentro i limiti: " + d.name);
            check(d.maxTorque >= 2.0, "coppia > 0: " + d.name);
        }

        // posa di riposo: lunghezze e posizioni attese
        BodyPose rest = a.pose(null, V3.ZERO, Rot.IDENTITY);
        double upper = a.link("leg_upper_L").length, lower = a.link("leg_lower_L").length;
        check(near(rest.get("foot_L").origin().y(), -(upper + lower), 1e-9), "caviglia a -(coscia+stinco)");
        check(near(rest.get("foot_L").origin().x(), rest.get("leg_upper_L").origin().x(), 1e-9), "gamba verticale");
        check(rest.get("leg_upper_L").origin().x() < 0 && rest.get("leg_upper_R").origin().x() > 0, "L a X negativo");
        check(rest.get("head").origin().y() > rest.get("torso").origin().y(), "testa sopra il torace");

        // ginocchio a 90 gradi: lo stinco va indietro (-Z), il piede segue
        double[] q = new double[a.dofCount()];
        q[a.dofIndex("knee_L")] = Math.PI / 2;
        BodyPose bent = a.pose(q, V3.ZERO, Rot.IDENTITY);
        V3 knee = bent.get("leg_lower_L").origin(), ankle = bent.get("foot_L").origin();
        check(near(ankle.z() - knee.z(), -lower, 1e-9) && near(ankle.y(), knee.y(), 1e-9), "flessione del ginocchio");
        // anca: flessione in avanti con angolo negativo
        q = new double[a.dofCount()];
        q[a.dofIndex("hip_R")] = -Math.PI / 2;
        V3 knee2 = a.pose(q, V3.ZERO, Rot.IDENTITY).get("leg_lower_R").origin();
        check(near(knee2.z(), upper, 1e-9), "anca: angolo negativo = gamba in avanti");
        // gomito: avambraccio in avanti con angolo negativo
        q = new double[a.dofCount()];
        q[a.dofIndex("elbow_L")] = -Math.PI / 2;
        BodyPose elb = a.pose(q, V3.ZERO, Rot.IDENTITY);
        check(elb.get("hand_L").origin().z() > elb.get("arm_lower_L").origin().z() + 0.1, "gomito: avambraccio in avanti");
        // il bacino ruotato di 90 gradi attorno a Y porta il "davanti" (+Z) su +X
        Rot yaw = Rot.axisAngle(new V3(0, 1, 0), Math.PI / 2);
        V3 f = yaw.apply(new V3(0, 0, 1));
        check(near(f.x(), 1, 1e-9) && near(f.z(), 0, 1e-9), "rotazione attorno a Y");

        // massa e appoggio al suolo
        double mass = a.totalMass();
        check(mass > 20 && mass < 400, "massa plausibile: " + String.format("%.1f", mass) + " kg");
        double off = a.groundOffset();
        check(near(a.pose(null, new V3(0, off, 0), Rot.IDENTITY).lowestPoint(), 0, 1e-9), "appoggio al suolo");
        Link ball = new Link("sfera", null, new V3(0, 1, 0), 0.2, 0.5, V3.ZERO, 1000);
        check(near(ball.radius, 0.1, 1e-12) && near(ball.inertiaAxial, ball.inertiaTransverse, 1e-12)
                && near(ball.inertiaAxial, 0.4 * ball.mass * 0.01, 1e-9), "capsula degenere = sfera piena");
        Link big = new Link("c", null, new V3(0, 1, 0), 1.0, 0.1, V3.ZERO, 1000);
        check(big.inertiaTransverse > big.inertiaAxial, "capsula lunga: inerzia trasversale > assiale");

        // crescita: un cucciolo e' piu' piccolo e leggero
        Automaton baby = Automaton.humanoid(new BodyParams(0.8, 0.15, 0.25, 1.0, 0.08, 0.15, 0.08, 0.2, BodyParams.growthFactor(0)));
        Automaton adult = Automaton.humanoid(new BodyParams(0.8, 0.15, 0.25, 1.0, 0.08, 0.15, 0.08, 0.2, 1.0));
        check(baby.totalMass() < adult.totalMass() * 0.2, "il cucciolo pesa molto meno dell'adulto");

        // collisioni tra segmenti
        V3 o = V3.ZERO;
        check(near(BodyCollision.segmentDistance(o, new V3(1, 0, 0), new V3(0, 2, 0), new V3(1, 2, 0)), 2, 1e-12), "segmenti paralleli");
        check(near(BodyCollision.segmentDistance(new V3(-1, 0, 0), new V3(1, 0, 0), new V3(0, -1, 1), new V3(0, 1, 1)), 1, 1e-12), "segmenti incrociati a distanza 1");
        check(near(BodyCollision.segmentDistance(o, o, new V3(3, 4, 0), new V3(3, 4, 0)), 5, 1e-12), "punto-punto");
        check(near(BodyCollision.segmentDistance(o, new V3(1, 0, 0), new V3(3, 0, 0), new V3(5, 0, 0)), 2, 1e-12), "segmenti allineati");
        BodyPose p1 = a.pose(null, new V3(0, off, 0), Rot.IDENTITY);
        BodyPose far = a.pose(null, new V3(10, off, 0), Rot.IDENTITY);
        BodyPose touching = a.pose(null, new V3(0.2, off, 0), Rot.IDENTITY);
        check(!BodyCollision.overlap(p1, far), "corpi lontani non si toccano");
        check(BodyCollision.overlap(p1, touching), "corpi sovrapposti si toccano");
        check(BodyCollision.strike(p1, a.extremities(), far, 0.5) == null, "nessun colpo a 10 m");
        // un pugno: braccio destro teso in avanti (con proporzioni medie la punta arriva a ~1.25 m dal bacino)
        double[] punch = new double[a.dofCount()];
        punch[a.dofIndex("shoulder_R")] = -Math.PI / 2;
        BodyPose striker = a.pose(punch, new V3(0, off, 0), Rot.IDENTITY);
        BodyPose victim = a.pose(null, new V3(0, off, 1.3), Rot.IDENTITY);
        BodyPose tooFar = a.pose(null, new V3(0, off, 1.6), Rot.IDENTITY);
        BodyCollision.Contact c = BodyCollision.strike(striker, a.extremities(), victim, 0.05);
        check(c != null && c.attackerLink().endsWith("_R"), "il pugno destro raggiunge il bersaglio a 1.3 m: " + c);
        check(BodyCollision.strike(striker, a.extremities(), tooFar, 0.05) == null, "a 1.6 m il pugno non arriva");
        check(BodyCollision.strike(a.pose(null, new V3(0, off, 0), Rot.IDENTITY), a.extremities(), victim, 0.05) == null, "senza pugno a 1.3 m non si colpisce");

        // URDF ben formato e coerente
        String urdf = BodyExport.urdf(a, "test");
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(urdf.getBytes(StandardCharsets.UTF_8)));
        var linkNodes = doc.getElementsByTagName("link");
        var jointNodes = doc.getElementsByTagName("joint");
        check(linkNodes.getLength() == 31, "URDF: 31 link con quelli virtuali (trovati " + linkNodes.getLength() + ")");
        check(jointNodes.getLength() == 30, "URDF: 30 giunti (trovati " + jointNodes.getLength() + ")");
        Set<String> children = new HashSet<>();
        for (int i = 0; i < jointNodes.getLength(); i++) {
            var el = (org.w3c.dom.Element) jointNodes.item(i);
            String child = ((org.w3c.dom.Element) el.getElementsByTagName("child").item(0)).getAttribute("link");
            check(children.add(child), "ogni link ha un solo genitore: " + child);
        }
        check(!children.contains("pelvis"), "il bacino e' la radice");

        // robustezza su 200 genomi casuali: nessun NaN, tutto finito e fisico
        Random rng = new Random(7);
        for (int k = 0; k < 200; k++) {
            Genome g = Genome.random(schema, rng, "t" + k, 0);
            Automaton r = Automaton.humanoid(BodyParams.fromGenome(g, schema, rng.nextInt(400)));
            boolean ok = r.totalMass() > 0 && Double.isFinite(r.totalMass()) && Double.isFinite(r.groundOffset());
            for (Dof d : r.dofs()) ok &= Double.isFinite(d.maxTorque) && d.maxTorque > 0;
            for (Link l : r.links()) ok &= l.mass > 0 && l.inertiaTransverse > 0 && l.inertiaAxial > 0;
            check(ok, "genoma casuale " + k + " produce un corpo valido");
        }

        System.out.println((checks - failures) + "/" + checks + " verifiche superate"
                + (failures > 0 ? ", " + failures + " FALLITE" : ""));
        if (failures > 0) System.exit(1);
    }
}
