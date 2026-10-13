package src.body;

/** Vettore 3D immutabile (double). Nessuna dipendenza: il modello del corpo gira anche senza grafica. */
public record V3(double x, double y, double z) {

    public static final V3 ZERO = new V3(0, 0, 0);

    public V3 add(V3 o) { return new V3(x + o.x, y + o.y, z + o.z); }
    public V3 sub(V3 o) { return new V3(x - o.x, y - o.y, z - o.z); }
    public V3 scale(double k) { return new V3(x * k, y * k, z * k); }
    public double dot(V3 o) { return x * o.x + y * o.y + z * o.z; }
    public V3 cross(V3 o) { return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    public double length() { return Math.sqrt(dot(this)); }

    public V3 normalized() {
        double l = length();
        return l < 1e-12 ? this : scale(1.0 / l);
    }
}
