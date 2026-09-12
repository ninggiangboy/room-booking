package dev.ngb.backend.model;

/**
 * Whether a modification costs more, costs less, or costs the same.
 *
 * <p>The direction decides which prerequisite the proposal must name before it can be committed: an
 * increase needs an obligation to collect it, a decrease needs an instruction to return it.</p>
 */
public enum ModificationDeltaDirection {
    /** No money moves. */
    ZERO,
    /** The guest owes more. */
    INCREASE,
    /** The guest is owed a return. */
    DECREASE
}
