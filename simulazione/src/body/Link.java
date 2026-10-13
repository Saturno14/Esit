package src.body;

/**
 * Segmento rigido a forma di capsula. L'origine del link e' il perno del suo giunto; "dir" e' la
 * direzione di riposo (assoluta, come in BoneRig), "length" la lunghezza totale della capsula
 * (calotte comprese), "geomOffset" lo scostamento della capsula rispetto all'origine (il piede
 * parte un po' dietro la caviglia). Massa e inerzia derivano dal volume con densita' costante.
 */
public final class Link {

    public final String name;
    public final String parent;
    public final V3 dir;
    public final double length;
    public final V3 geomOffset;
    public final double radius;
    public final double mass;
    /** Momento d'inerzia attorno all'asse della capsula e attorno a un asse perpendicolare (dal centro). */
    public final double inertiaAxial;
    public final double inertiaTransverse;

    Link(String name, String parent, V3 dir, double length, double requestedRadius, V3 geomOffset,
         double density) {
        this.name = name;
        this.parent = parent;
        this.dir = dir.normalized();
        this.length = Math.max(length, 1e-4);
        // la capsula non puo' essere piu' larga di meta' della sua lunghezza (altrimenti diventa una sfera)
        this.radius = Math.max(1e-4, Math.min(requestedRadius, this.length / 2.0));
        this.geomOffset = geomOffset;

        double r = this.radius, h = cylinderHeight();
        double mc = density * Math.PI * r * r * h;                    // cilindro centrale
        double ms = density * (4.0 / 3.0) * Math.PI * r * r * r;      // le due calotte = una sfera
        this.mass = mc + ms;
        this.inertiaAxial = mc * r * r / 2.0 + 0.4 * ms * r * r;
        this.inertiaTransverse = mc * (r * r / 4.0 + h * h / 12.0)
                + ms * (0.4 * r * r + h * h / 4.0 + 3.0 * h * r / 8.0);
    }

    /** Altezza della parte cilindrica (0 se la capsula e' una sfera). */
    public double cylinderHeight() { return Math.max(0.0, length - 2.0 * radius); }

    public V3 start() { return geomOffset; }
    public V3 end() { return geomOffset.add(dir.scale(length)); }
    public V3 center() { return geomOffset.add(dir.scale(length / 2.0)); }
    /** Estremi del segmento interno della capsula (centri delle due calotte). */
    public V3 coreA() { return geomOffset.add(dir.scale(radius)); }
    public V3 coreB() { return geomOffset.add(dir.scale(length - radius)); }
}
