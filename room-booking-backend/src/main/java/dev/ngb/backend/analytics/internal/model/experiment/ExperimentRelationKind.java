package dev.ngb.backend.analytics.internal.model.experiment;

/**
 * How one epoch relates to another experiment.
 */
public enum ExperimentRelationKind {

    /** One unit may not carry both treatments. */
    MUTUALLY_EXCLUSIVE,

    /** This epoch is only interpretable alongside the other. */
    REQUIRED_CO_EXPERIMENT,

    /** The two interact in a way the analysis has to account for. */
    KNOWN_INTERACTION
}
