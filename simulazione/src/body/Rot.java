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

    /**
     * Rotazione inversa. Per una matrice di rotazione l'inversa e' la trasposta. Serve ogni volta
     * che si vuole un orientamento RELATIVO: quello di un figlio rispetto al genitore, per
     * esempio, e' inverse(genitore) * figlio.
     */
    public Rot inverse() {
        return new Rot(new double[]{m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]});
    }

    /** Rotazione attorno all'asse Y (imbardata): l'orientamento con cui un'entita' sta in piedi. */
    public static Rot yaw(double angle) {
        return axisAngle(new V3(0, 1, 0), angle);
    }

    /**
     * Matrice equivalente a un quaternione (x,y,z,w). Serve per leggere l'orientamento che arriva
     * dal motore fisico, che lavora con quaternioni, e riportarlo nelle pose dei link.
     */
    public static Rot quaternion(double x, double y, double z, double w) {
        double n = Math.sqrt(x * x + y * y + z * z + w * w);
        if (n < 1e-12) {
            return IDENTITY;
        }
        x /= n; y /= n; z /= n; w /= n;
        double xx = x * x, yy = y * y, zz = z * z;
        double xy = x * y, xz = x * z, yz = y * z;
        double wx = w * x, wy = w * y, wz = w * z;
        return new Rot(new double[]{
                1 - 2 * (yy + zz), 2 * (xy - wz), 2 * (xz + wy),
                2 * (xy + wz), 1 - 2 * (xx + zz), 2 * (yz - wx),
                2 * (xz - wy), 2 * (yz + wx), 1 - 2 * (xx + yy)});
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
