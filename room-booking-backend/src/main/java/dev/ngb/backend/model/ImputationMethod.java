package dev.ngb.backend.model;

/**
 * How a substituted feature value was produced.
 */
public enum ImputationMethod {

    /** A declared constant. */
    CONSTANT,

    /** The entity's previous value, carried forward. */
    LAST_OBSERVED,

    /** The population median at the time of the build. */
    POPULATION_MEDIAN
}
