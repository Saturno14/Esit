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

    /**
     * Questa rotazione come VETTORE asse-angolo: direzione = asse, lunghezza = angolo in
     * radianti. Serve alla propriocezione: proiettandolo sull'asse di un grado di liberta' si
     * ottiene di quanto quel grado di liberta' e' ruotato, qualunque sia la forma del giunto.
     * E' esatto per una rotazione attorno a un asse solo, ed e' la migliore
     * approssimazione lineare quando gli assi sono piu' di uno - che e' la stessa
     * approssimazione che fa il vincolo a sei gradi di liberta' in JoltRagdoll.
     */
    public V3 rotationVector() {
        double trace = m[0] + m[4] + m[8];
        double cos = Math.max(-1, Math.min(1, (trace - 1) / 2.0));
        double angle = Math.acos(cos);
        if (angle < 1e-9) {
            return V3.ZERO;
        }
        double sin = Math.sin(angle);
        if (sin < 1e-9) {
            // mezzo giro: l'asse si legge dalla parte simmetrica, il segno non e' determinato
            V3 axis = new V3(Math.sqrt(Math.max(0, (m[0] + 1) / 2)),
                    Math.sqrt(Math.max(0, (m[4] + 1) / 2)),
                    Math.sqrt(Math.max(0, (m[8] + 1) / 2))).normalized();
            return axis.scale(angle);
        }
        V3 axis = new V3(m[7] - m[5], m[2] - m[6], m[3] - m[1]).scale(1.0 / (2 * sin));
        return axis.scale(angle);
    }

    /**
     * Di quanto questo orientamento e' fuori piombo: l'asse "su" portato nel mondo dice da che
     * parte pende. [beccheggio, rollio] in radianti.
     *
     * Stava in Balance, che era un controllore scritto a mano e non c'e' piu'. Questa invece e'
     * geometria: dice COME STA il corpo, non cosa deve fare, quindi e' un SENSO, e vive qui.
     */
    public double[] tilt() {
        V3 up = apply(new V3(0, 1, 0));
        double pitch = Math.atan2(up.z(), Math.max(1e-6, up.y()));
        double roll = Math.atan2(up.x(), Math.max(1e-6, up.y()));
        return new double[]{pitch, roll};
    }
}
