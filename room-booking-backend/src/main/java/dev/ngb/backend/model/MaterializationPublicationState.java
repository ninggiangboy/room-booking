package dev.ngb.backend.model;

/**
 * Standing of one computed number.
 *
 * <p>A restated number supersedes rather than overwrites, so a figure somebody already quoted
 * remains findable.</p>
 */
public enum MaterializationPublicationState {

    /** Computed but not the number a report should show. */
    PROVISIONAL,

    /** The published value for this metric, slice and window. */
    CURRENT,

    /** Superseded by a later computation that names it. */
    RESTATED,

    /** Pulled without a replacement. */
    WITHDRAWN
}
