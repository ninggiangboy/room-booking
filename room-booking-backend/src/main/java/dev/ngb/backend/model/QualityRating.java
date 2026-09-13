package dev.ngb.backend.model;

/**
 * How one dimension of a decision scored in quality review.
 */
public enum QualityRating {

    /** The dimension was handled as policy requires. */
    MEETS,

    /** A shortfall worth coaching but not correction. */
    MINOR_GAP,

    /** A shortfall that needs a correction of some kind. */
    MAJOR_GAP,

    /** The dimension does not arise on this case. */
    NOT_APPLICABLE
}
