package com.drmangotea.tfmg.content.items.inspector;

/**
 * A block entity that can explain itself to the Factory Inspector: what it
 * needs, what it has, and what stops it. Called on the server only.
 *
 * @author vyrriox
 */
public interface IInspectable {

    void inspect(InspectionReport report);

    /**
     * False for kinetic blocks that never take rotation from a shaft (the
     * electric pump, the large engine...), so the generic "not turning"
     * check does not flag them.
     */
    default boolean wantsRotationCheck() {
        return true;
    }
}
