package src.body;

/** Matrice di rotazione 3x3 (riga per riga), immutabile. */
public final class Rot {

    public static final Rot IDENTITY = new Rot(new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1});

    private final double[] m;

    private Rot(double[] m) { this.m = m; }

    /** Rotazione di "angle" radianti attorno a "axis" (regola della mano destra, formula di Rodrigues). */
    public static Rot axisAngle(V3 axis, double angle) {
        V3 k = axis.normalized();
        double c = Math.cos(angle), s = Math.sin(angle), t = 1 - c;
        return new Rot(new double[]{
                t * k.x() * k.x() + c,        t * k.x() * k.y() - s * k.z(), t * k.x() * k.z() + s * k.y(),
                t * k.x() * k.y() + s * k.z(), t * k.y() * k.y() + c,        t * k.y() * k.z() - s * k.x(),
                t * k.x() * k.z() - s * k.y(), t * k.y() * k.z() + s * k.x(), t * k.z() * k.z() + c});
    }

    public Rot mul(Rot o) {
        double[] r = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = m[i * 3] * o.m[j] + m[i * 3 + 1] * o.m[3 + j] + m[i * 3 + 2] * o.m[6 + j];
            }
        }
        return new Rot(r);
    }

    public V3 apply(V3 v) {
        return new V3(
                m[0] * v.x() + m[1] * v.y() + m[2] * v.z(),
                m[3] * v.x() + m[4] * v.y() + m[5] * v.z(),
                m[6] * v.x() + m[7] * v.y() + m[8] * v.z());
    }

    public double get(int row, int col) { return m[row * 3 + col]; }
}
