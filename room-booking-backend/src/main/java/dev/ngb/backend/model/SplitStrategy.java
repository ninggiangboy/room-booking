package dev.ngb.backend.model;

/**
 * How a training dataset was divided into train, validation and test.
 *
 * <p>RANDOM is refused for time-dependent behaviour: the same booking, host, guest or near
 * duplicate listing on both sides of the boundary produces a score that measures the leak.</p>
 */
public enum SplitStrategy {

    /** Train, validate and test on successive time windows. */
    TEMPORAL,

    /** Keep every example of one entity on one side of the boundary. */
    ENTITY_GROUPED,

    /** Both: successive windows, with entities kept whole. */
    TEMPORAL_ENTITY_GROUPED,

    /** Row-wise at random; valid only where nothing is time-dependent. */
    RANDOM
}
