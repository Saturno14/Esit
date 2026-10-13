package src.body;

import java.util.List;

/**
 * Giunto anatomico tra due link. "offset" e' la posizione del perno rispetto all'origine del link
 * genitore (assi del corpo, a riposo). Senza gradi di liberta' il giunto e' fisso; con piu' gradi
 * (spalla, anca...) equivale a una sfera, decomposta in cerniere in serie: e' la forma che
 * accettano URDF, Isaac e MuJoCo.
 */
public record Joint(String name, String parent, String child, V3 offset, List<Dof> dofs) {

    public boolean isFixed() { return dofs.isEmpty(); }
}
