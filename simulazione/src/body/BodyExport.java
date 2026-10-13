package src.body;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Esporta un {@link Automaton} per i simulatori fisici esterni (Isaac Sim/Lab, MuJoCo, PyBullet).
 *
 * URDF: Z in alto e X avanti (convenzione ROS), quindi (x,y,z) del corpo diventa (z,x,y). Il giunto
 * sferico non esiste in URDF: ogni giunto con n gradi di liberta' diventa una catena di n cerniere
 * con n-1 link virtuali (massa trascurabile). La capsula e' scritta come cilindro + due sfere.
 */
public final class BodyExport {

    private static final double VIRTUAL_MASS = 0.01;
    private static final double VIRTUAL_INERTIA = 1e-5;

    private BodyExport() {
    }

    private static V3 u(V3 v) { return new V3(v.z(), v.x(), v.y()); }

    private static String f(double v) { return String.format(Locale.ROOT, "%.10f", v); }
    private static String e(double v) { return String.format(Locale.ROOT, "%.8e", v); }
    private static String xyz(V3 v) { return f(v.x()) + " " + f(v.y()) + " " + f(v.z()); }

    public static String urdf(Automaton a, String robotName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?>\n<robot name=\"").append(robotName).append("\">\n");
        for (Link l : a.links()) {
            appendLink(sb, l);
        }
        for (Joint j : a.joints()) {
            appendJoint(sb, j);
        }
        sb.append("</robot>\n");
        return sb.toString();
    }

    private static void appendLink(StringBuilder sb, Link l) {
        V3 d = u(l.dir);
        V3 center = u(l.center());
        // tensore d'inerzia nel frame del corpo: Itr*I + (Iax - Itr) * d d^T, poi permutazione degli assi
        double[][] ib = new double[3][3];
        double[] db = {l.dir.x(), l.dir.y(), l.dir.z()};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                ib[i][j] = (i == j ? l.inertiaTransverse : 0) + (l.inertiaAxial - l.inertiaTransverse) * db[i] * db[j];
            }
        }
        int[] p = {2, 0, 1};
        sb.append("  <link name=\"").append(l.name).append("\">\n    <inertial>\n      <origin xyz=\"")
                .append(xyz(center)).append("\" rpy=\"0 0 0\"/>\n      <mass value=\"").append(e(l.mass))
                .append("\"/>\n      <inertia ixx=\"").append(e(ib[p[0]][p[0]])).append("\" iyy=\"")
                .append(e(ib[p[1]][p[1]])).append("\" izz=\"").append(e(ib[p[2]][p[2]])).append("\" ixy=\"")
                .append(e(ib[p[0]][p[1]])).append("\" ixz=\"").append(e(ib[p[0]][p[2]])).append("\" iyz=\"")
                .append(e(ib[p[1]][p[2]])).append("\"/>\n    </inertial>\n");
        for (String tag : new String[]{"visual", "collision"}) {
            double h = l.cylinderHeight();
            if (h > 1e-6) {
                sb.append("    <").append(tag).append(">\n      <origin xyz=\"").append(xyz(center)).append("\" rpy=\"")
                        .append(rpyZTo(d)).append("\"/>\n      <geometry><cylinder radius=\"").append(f(l.radius))
                        .append("\" length=\"").append(f(h)).append("\"/></geometry>\n    </").append(tag).append(">\n");
            }
            V3[] caps = h > 1e-6 ? new V3[]{u(l.coreA()), u(l.coreB())} : new V3[]{center};
            for (V3 c : caps) {
                sb.append("    <").append(tag).append(">\n      <origin xyz=\"").append(xyz(c))
                        .append("\" rpy=\"0 0 0\"/>\n      <geometry><sphere radius=\"").append(f(l.radius))
                        .append("\"/></geometry>\n    </").append(tag).append(">\n");
            }
        }
        sb.append("  </link>\n");
    }

    private static void appendJoint(StringBuilder sb, Joint j) {
        int n = j.dofs().size();
        if (n == 0) {
            sb.append("  <joint name=\"").append(j.name()).append("\" type=\"fixed\">\n    <parent link=\"")
                    .append(j.parent()).append("\"/>\n    <child link=\"").append(j.child()).append("\"/>\n    <origin xyz=\"")
                    .append(xyz(u(j.offset()))).append("\" rpy=\"0 0 0\"/>\n  </joint>\n");
            return;
        }
        String prev = j.parent();
        for (int k = 0; k < n; k++) {
            Dof d = j.dofs().get(k);
            boolean last = k == n - 1;
            String child = last ? j.child() : j.name() + "_v" + (k + 1);
            if (!last) {
                sb.append("  <link name=\"").append(child).append("\">\n    <inertial>\n      <origin xyz=\"0 0 0\" rpy=\"0 0 0\"/>\n")
                        .append("      <mass value=\"").append(e(VIRTUAL_MASS)).append("\"/>\n      <inertia ixx=\"")
                        .append(e(VIRTUAL_INERTIA)).append("\" iyy=\"").append(e(VIRTUAL_INERTIA)).append("\" izz=\"")
                        .append(e(VIRTUAL_INERTIA)).append("\" ixy=\"0\" ixz=\"0\" iyz=\"0\"/>\n    </inertial>\n  </link>\n");
            }
            sb.append("  <joint name=\"").append(d.name).append("\" type=\"revolute\">\n    <parent link=\"").append(prev)
                    .append("\"/>\n    <child link=\"").append(child).append("\"/>\n    <origin xyz=\"")
                    .append(k == 0 ? xyz(u(j.offset())) : "0 0 0").append("\" rpy=\"0 0 0\"/>\n    <axis xyz=\"")
                    .append(xyz(u(d.axis))).append("\"/>\n    <limit lower=\"").append(f(d.lower)).append("\" upper=\"")
                    .append(f(d.upper)).append("\" effort=\"").append(f(d.maxTorque)).append("\" velocity=\"")
                    .append(f(d.maxVelocity)).append("\"/>\n  </joint>\n");
            prev = child;
        }
    }

    /** rpy (convenzione URDF: R = Rz(yaw) Ry(pitch) Rx(roll)) che porta l'asse Z del cilindro lungo "d". */
    static String rpyZTo(V3 d) {
        V3 h = Math.abs(d.x()) > 0.9 ? new V3(0, 1, 0) : new V3(1, 0, 0);
        V3 uAxis = h.sub(d.scale(h.dot(d))).normalized();
        V3 v = d.cross(uAxis);
        // colonne della rotazione: uAxis, v, d
        double r00 = uAxis.x(), r10 = uAxis.y(), r20 = uAxis.z();
        double r01 = v.x(), r11 = v.y(), r21 = v.z(), r22 = d.z();
        double pitch = -Math.asin(Math.max(-1, Math.min(1, r20)));
        double roll, yaw;
        if (Math.abs(r20) > 0.999999) {
            roll = 0;
            yaw = Math.atan2(-r01, r11);
        } else {
            roll = Math.atan2(r21, r22);
            yaw = Math.atan2(r10, r00);
        }
        return f(roll) + " " + f(pitch) + " " + f(yaw);
    }

    /** Descrizione leggibile dalle librerie Python: ordine dei gradi di liberta', limiti, coppie e link. */
    public static JSONObject spec(Automaton a, String robotName) {
        JSONObject o = new JSONObject();
        o.put("robot", robotName);
        o.put("convention", "corpo: X laterale (L=-X), Y su, Z avanti; URDF: x avanti, y sinistra, z su; SI");
        o.put("total_mass_kg", a.totalMass());
        o.put("ground_offset_m", a.groundOffset());
        o.put("growth", a.growth);
        o.put("plan", a.plan.toJson());
        JSONArray dofs = new JSONArray();
        for (Dof d : a.dofs()) {
            JSONObject jd = new JSONObject();
            jd.put("name", d.name);
            jd.put("axis_body", new JSONArray(new double[]{d.axis.x(), d.axis.y(), d.axis.z()}));
            jd.put("lower", d.lower); jd.put("upper", d.upper);
            jd.put("max_torque", d.maxTorque); jd.put("max_velocity", d.maxVelocity);
            dofs.put(jd);
        }
        o.put("dofs", dofs);
        JSONArray links = new JSONArray();
        for (Link l : a.links()) {
            JSONObject jl = new JSONObject();
            jl.put("name", l.name); jl.put("parent", l.parent == null ? JSONObject.NULL : l.parent);
            jl.put("mass", l.mass); jl.put("length", l.length); jl.put("radius", l.radius);
            links.put(jl);
        }
        o.put("links", links);
        return o;
    }
}
