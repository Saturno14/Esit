package com.saturno14.esit.graphics.flora;

import src.Flora;

import java.util.ArrayList;
import java.util.List;

/**
 * Legge dalla simulazione (solo lettura) alberi e mele da disegnare.
 *
 * NON SCANDISCE PIU' LA GRIGLIA. La vecchia AppleField passava in rassegna 125000 celle a ogni
 * frame per trovare qualche centinaio di mele. Adesso la mela e' un oggetto con una posizione
 * continua propria (vedi src.Flora), quindi basta chiedere la lista: il costo e' proporzionale a
 * quante mele ci sono, non a quanto e' grande il mondo. In piu' una mela sulla chioma sta in aria
 * a meta' tra due celle, e una griglia di interi non avrebbe potuto rappresentarla.
 */
public final class FloraField {

    /** Una mela pronta da disegnare: posizione in metri, grandezza e maturazione. */
    public record Apple(float x, float y, float z, float size, float ripeness, boolean onTree) {
    }

    /** Un albero pronto da disegnare: piede, altezza e raggio della chioma in metri. */
    public record Tree(float x, float baseY, float z, float height, float crownRadius, float maturity) {
    }

    private FloraField() {
    }

    public static List<Apple> apples() {
        List<Flora.Apple> src = Flora.snapshotApples();
        List<Apple> out = new ArrayList<>(src.size());
        for (Flora.Apple a : src) {
            out.add(new Apple((float) a.x, (float) a.y, (float) a.z,
                    (float) a.sizeFactor(), (float) a.ripeness(), a.onTree));
        }
        return out;
    }

    public static List<Tree> trees() {
        List<Flora.Tree> src = Flora.snapshotTrees();
        List<Tree> out = new ArrayList<>(src.size());
        for (Flora.Tree t : src) {
            out.add(new Tree(t.x + 0.5f, t.baseY, t.z + 0.5f,
                    (float) t.height(), (float) t.crownNow(), (float) t.maturity()));
        }
        return out;
    }
}
