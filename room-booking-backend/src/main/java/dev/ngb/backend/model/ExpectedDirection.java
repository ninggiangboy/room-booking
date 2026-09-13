package dev.ngb.backend.model;

/**
 * Which way a declared metric is expected to move under treatment.
 *
 * <p>Stated before launch, so that a move in the other direction cannot be reinterpreted afterwards
 * as the intended outcome.</p>
 */
public enum ExpectedDirection {

    /** Expected to go up. */
    INCREASE,

    /** Expected to go down. */
    DECREASE,

    /** Expected to hold, which is what a guardrail usually asserts. */
    NO_CHANGE
}
