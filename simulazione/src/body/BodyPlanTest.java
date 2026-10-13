package src.body;

import org.json.JSONObject;
import src.genetics.BodyPlan;
import src.genetics.Genome;
import src.genetics.GenomeSchema;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Verifiche sul corpo completamente mutabile: serpente, arti in piu', specchio, genoma, evoluzione estrema.
 *   java -cp "target\classes;<json.jar>;simulazione\src\genetics\resources" src.body.BodyPlanTest
 */
public final class BodyPlanTest {

    private static int failures = 0, checks = 0;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FALLITO: " + what);
        }
    }

    private static boolean near(double a, double b, double tol) { return Math.abs(a - b) <= tol; }

    private static BodyPlan humanoid() { return BodyPlan.humanoid(0.8, 0.15, 0.25, 1.0, 0.08, 0.15, 0.08, 0.2); }

    /** Controlli validi per QUALSIASI corpo: finito, fisico, URDF ben formato e ad albero. Ritorna i gdl. */
    private static int checkBody(BodyPlan plan, double growth, String label) throws Exception {
        Automaton a = Automaton.fromPlan(plan, growth);
        boolean ok = a.links().size() == plan.size() && a.totalMass() > 0 && Double.isFinite(a.totalMass())
                && Double.isFinite(a.groundOffset());
        for (Dof d : a.dofs()) ok &= Double.isFinite(d.maxTorque) && d.maxTorque > 0 && d.lower < d.upper;
        for (Link l : a.links()) ok &= l.mass > 0 && l.inertiaTransverse > 0 && l.inertiaAxial > 0;
        double[] q = new double[a.dofCount()];
        Random rq = new Random(label.hashCode());
        for (int i = 0; i < q.length; i++) q[i] = a.dofs().get(i).lower + rq.nextDouble() * (a.dofs().get(i).upper - a.dofs().get(i).lower);
        for (BodyPose.LinkPose lp : a.pose(q, V3.ZERO, Rot.IDENTITY).links()) {
            ok &= Double.isFinite(lp.coreA().x() + lp.coreA().y() + lp.coreA().z() + lp.coreB().x() + lp.coreB().y() + lp.coreB().z());
        }
        Set<String> dofNames = new HashSet<>();
        for (Dof d : a.dofs()) ok &= dofNames.add(d.name);
        String urdf = BodyExport.urdf(a, "t");
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(urdf.getBytes(StandardCharsets.UTF_8)));
        var joints = doc.getElementsByTagName("joint");
        Set<String> children = new HashSet<>();
        int fixed = 0;
        for (Joint j : a.joints()) if (j.isFixed()) fixed++;
        int expectedJoints = a.dofCount() + fixed;
        ok &= joints.getLength() == expectedJoints;
        for (int i = 0; i < joints.getLength(); i++) {
            var el = (org.w3c.dom.Element) joints.item(i);
            ok &= children.add(((org.w3c.dom.Element) el.getElementsByTagName("child").item(0)).getAttribute("link"));
        }
        check(ok, label + ": corpo valido (massa, inerzia, FK, URDF ad albero)");
        return a.dofCount();
    }

    public static void main(String[] args) throws Exception {
        GenomeSchema schema = GenomeSchema.loadFromClasspath("/gene_schema.json");

        // --- serpente: nessun arto, catena di segmenti
        BodyPlan snake = BodyPlan.snake(12, 0.4, 0.08);
        Automaton sn = Automaton.fromPlan(snake, 1.0);
        check(sn.dofCount() == 22 && sn.links().size() == 12, "serpente: 12 link e 22 gdl (trovati " + sn.links().size() + "/" + sn.dofCount() + ")");
        check(sn.extremities().size() == 1 && sn.extremities().contains("body11"), "serpente: una sola estremita' (la coda)");
        checkBody(snake, 1.0, "serpente");
        double[] q = new double[sn.dofCount()];
        for (int i = 0; i < q.length; i += 2) q[i] = 0.5;       // tutte le imbardate uguali: il corpo si incurva nel piano
        BodyPose curled = sn.pose(q, V3.ZERO, Rot.IDENTITY);
        check(Math.abs(curled.get("body11").origin().x()) > 0.3 && near(curled.get("body11").origin().y(), 0, 1e-9), "serpente: si incurva restando orizzontale");
        check(sn.groundOffset() > 0 && near(sn.pose(null, new V3(0, sn.groundOffset(), 0), Rot.IDENTITY).lowestPoint(), 0, 1e-9), "serpente: appoggiato al suolo");

        // --- da umanoide a senza arti / a solo torso
        BodyPlan h = humanoid();
        BodyPlan noArms = h.withoutSubtree("arm_upper_L").withoutSubtree("arm_upper_R");
        check(noArms.size() == 10 && !noArms.has("hand_L"), "senza braccia: 10 segmenti");
        BodyPlan legless = noArms.withoutSubtree("leg_upper_L").withoutSubtree("leg_upper_R");
        check(legless.size() == 4, "senza arti: pelvis, torso, collo, testa");
        check(checkBody(legless, 1.0, "senza arti") == 5, "senza arti: 5 gdl (colonna 3 + collo 2)");

        // --- terzo braccio: duplico il braccio destro, specchiato
        BodyPlan three = h.withDuplicatedSubtree("arm_upper_R", true, new Random(1));
        Automaton t3 = Automaton.fromPlan(three, 1.0);
        check(three.size() == 22 && t3.dofCount() == 25 + 4 - 0 && t3.extremities().size() == 6,
                "terzo braccio: 22 segmenti, 29 gdl, 6 estremita' (trovati " + three.size() + "/" + t3.dofCount() + "/" + t3.extremities().size() + ")");
        checkBody(three, 1.0, "terzo braccio");
        // la copia specchiata, con gli stessi angoli, si muove in modo speculare all'originale
        String copyTop = null;
        for (BodyPlan.Seg s : three.segments()) if (!h.has(s.id()) && s.parent().equals("torso")) copyTop = s.id();
        Joint jOrig = t3.jointOf("arm_upper_R"), jCopy = t3.jointOf(copyTop);
        double[] qq = new double[t3.dofCount()];
        for (int k = 0; k < jOrig.dofs().size(); k++) {
            double ang = new double[]{-1.0, 0.4, 0.3}[k];
            qq[t3.dofIndex(jOrig.name()) + k] = ang;
            qq[t3.dofIndex(jCopy.name()) + k] = ang;
        }
        BodyPose pp = t3.pose(qq, V3.ZERO, Rot.IDENTITY);
        String tipOrig = "finger_R", tipCopy = null;
        for (BodyPlan.Seg s : three.segments()) if (!h.has(s.id()) && t3.extremities().contains(s.id())) tipCopy = s.id();
        V3 o = pp.get(tipOrig).coreB(), c = pp.get(tipCopy).coreB();
        check(near(o.x(), -c.x(), 1e-9) && near(o.y(), c.y(), 1e-9) && near(o.z(), c.z(), 1e-9), "specchio: la copia si muove speculare all'originale");

        // --- serializzazione del piano
        BodyPlan back = BodyPlan.fromJson(new JSONObject(three.toJson().toString()));
        check(back.toJson().toString().equals(three.toJson().toString()), "JSON del piano: andata e ritorno identici");

        // --- genoma: piano alla nascita, salvataggio, compatibilita' con i salvataggi vecchi
        Random rng = new Random(3);
        Genome g1 = Genome.random(schema, rng, "a", 0), g2 = Genome.random(schema, rng, "b", 0);
        check(g1.bodyPlan(schema).size() == 18, "fondatore: umanoide a 18 segmenti");
        Genome rt = Genome.fromJson(new JSONObject(g1.toJson().toString()));
        check(rt.bodyPlan(schema).toJson().toString().equals(g1.bodyPlan(schema).toJson().toString()), "genoma: il piano sopravvive al salvataggio");
        JSONObject legacy = new JSONObject(g1.toJson().toString());
        legacy.remove("body");
        Genome old = Genome.fromJson(legacy);
        check(old.bodyPlan(schema).size() == 18 && old.bodyPlan(schema).toJson().toString().equals(g1.bodyPlan(schema).toJson().toString()),
                "salvataggio vecchio (senza body): ricostruito identico dagli alleli");
        Genome.BODY_MUTATION = BodyPlan.MutationRates.NONE;
        Genome kid = Genome.reproduce(g1, g2, schema, rng, "k", 1);
        check(kid.bodyPlan(schema).size() == 18, "figlio senza mutazioni strutturali: ancora 18 segmenti");
        Genome.BODY_MUTATION = BodyPlan.MutationRates.DEFAULT;

        // --- evoluzione estrema: due linee di sangue che si incrociano con mutazioni molto frequenti
        BodyPlan a = humanoid(), b = BodyPlan.humanoid(1.0, 0.2, 0.3, 1.2, 0.1, 0.2, 0.1, 0.25);
        int minSeg = 99, maxSeg = 0, minDof = 999, maxDof = 0, nonHumanoid = 0, moreThanTwoLegsLike = 0;
        Set<Integer> leafCounts = new HashSet<>();
        for (int gen = 0; gen < 400; gen++) {
            BodyPlan childA = BodyPlan.cross(a, b, rng).mutate(rng, BodyPlan.MutationRates.WILD);
            BodyPlan childB = BodyPlan.cross(b, a, rng).mutate(rng, BodyPlan.MutationRates.WILD);
            a = childA;
            b = childB;
            for (BodyPlan p : new BodyPlan[]{a, b}) {
                int dofs = checkBody(p, gen % 2 == 0 ? 1.0 : 0.35, "evoluzione gen " + gen);
                minSeg = Math.min(minSeg, p.size()); maxSeg = Math.max(maxSeg, p.size());
                minDof = Math.min(minDof, dofs); maxDof = Math.max(maxDof, dofs);
                leafCounts.add(p.leafCount());
                if (p.size() != 18) nonHumanoid++;
                check(p.size() <= BodyPlan.MutationRates.WILD.maxSegments() + 20, "tetto ai segmenti rispettato");
                check(BodyPlan.fromJson(new JSONObject(p.toJson().toString())).toJson().toString().equals(p.toJson().toString()), "JSON stabile gen " + gen);
            }
        }
        check(nonHumanoid > 100, "la struttura cambia davvero (" + nonHumanoid + " corpi non umanoidi su 800)");
        check(leafCounts.size() > 5, "varieta' di estremita': " + leafCounts);
        // determinismo: stessi semi, stessi corpi
        BodyPlan d1 = humanoid().mutate(new Random(9), BodyPlan.MutationRates.WILD);
        BodyPlan d2 = humanoid().mutate(new Random(9), BodyPlan.MutationRates.WILD);
        check(d1.toJson().toString().equals(d2.toJson().toString()), "mutazione deterministica a parita' di seme");
        // incrocio tra corpi senza antenati comuni (umanoide x serpente)
        BodyPlan mix = BodyPlan.cross(humanoid(), snake, rng);
        checkBody(mix, 1.0, "umanoide x serpente");

        System.out.println("Evoluzione estrema (800 corpi): segmenti " + minSeg + ".." + maxSeg + ", gdl " + minDof + ".." + maxDof
                + ", numero di estremita' viste: " + new java.util.TreeSet<>(leafCounts));
        System.out.println((checks - failures) + "/" + checks + " verifiche superate" + (failures > 0 ? ", " + failures + " FALLITE" : ""));
        if (failures > 0) System.exit(1);
    }
}
