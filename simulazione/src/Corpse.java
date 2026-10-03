package src;

/**
 * Cadavere di un'entita': resta nella cella in cui e' morta finche' non scade (expireTick,
 * in tick di simulazione) o non viene completamente mangiato. Ogni morso consuma parte della
 * riserva di carne.
 */
public class Corpse {

    private final entity body;
    private final int x, y, z;
    private double meat;
    private final long expireTick;

    public Corpse(entity body, int x, int y, int z, double meat, long expireTick) {
        this.body = body;
        this.x = x;
        this.y = y;
        this.z = z;
        this.meat = meat;
        this.expireTick = expireTick;
    }

    public entity getBody() { return body; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public double getMeat() { return meat; }
    public long getExpireTick() { return expireTick; }

    public boolean isConsumed() { return meat <= 0; }

    /** Toglie fino a "amount" di carne e ritorna quanta ne e' stata davvero presa. */
    public synchronized double takeBite(double amount) {
        double taken = Math.min(amount, meat);
        meat -= taken;
        return taken;
    }
}
