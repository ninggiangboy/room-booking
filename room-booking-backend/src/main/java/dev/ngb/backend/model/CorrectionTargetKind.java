package dev.ngb.backend.model;

/**
 * What a correction addresses.
 */
public enum CorrectionTargetKind {

    /** One named event. */
    EVENT,

    /** One partition of one dataset version. */
    DATASET_PARTITION,

    /** One published metric value. */
    METRIC_MATERIALIZATION
}
