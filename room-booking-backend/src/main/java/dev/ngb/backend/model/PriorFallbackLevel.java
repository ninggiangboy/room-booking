package dev.ngb.backend.model;

/**
 * How far up the geographic hierarchy a prior had to be taken from.
 *
 * <p>{@code NONE} means the subject had evidence of its own.</p>
 */
public enum PriorFallbackLevel {

    /** The subject had evidence of its own; no prior was needed. */
    NONE,

    /** Shrunk towards the neighbourhood prior. */
    NEIGHBORHOOD,

    /** Shrunk towards the locality prior. */
    LOCALITY,

    /** Shrunk towards the administrative area prior. */
    ADMIN_AREA,

    /** Shrunk towards the country prior. */
    COUNTRY,

    /** Shrunk towards the marketplace-wide prior, the last resort. */
    GLOBAL
}
