package src.body;

/**
 * Un grado di liberta' (una cerniera). "axis" e' espresso negli assi del corpo a riposo (X laterale,
 * Y su, Z avanti); l'angolo positivo e' la rotazione antioraria vista dalla punta dell'asse.
 * maxTorque e' un valore euristico (vedi Automaton.assignTorques), da calibrare in addestramento.
 */
public final class Dof {

    public final String name;
    public final V3 axis;
    public final double lower;
    public final double upper;
    public final double maxVelocity;
    public double maxTorque;

    Dof(String name, V3 axis, double lower, double upper, double maxVelocity) {
        this.name = name;
        this.axis = axis;
        this.lower = lower;
        this.upper = upper;
        this.maxVelocity = maxVelocity;
    }
}
