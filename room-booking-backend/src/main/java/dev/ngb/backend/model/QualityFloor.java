package dev.ngb.backend.model;

/**
 * The weakest input standing a metric will accept.
 */
public enum QualityFloor {

    /** Only clean inputs. */
    PASS,

    /** Warning-grade inputs are acceptable. */
    WARN
}
