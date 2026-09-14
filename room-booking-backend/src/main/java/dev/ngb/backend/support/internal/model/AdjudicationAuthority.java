package dev.ngb.backend.support.internal.model;

/**
 * The determination authority of {@code coverage_snapshots}.
 */
public enum AdjudicationAuthority {

    /** Platform deterministic. */
    PLATFORM_DETERMINISTIC,

    /** Platform with approval. */
    PLATFORM_WITH_APPROVAL,

    /** Provider administrator. */
    PROVIDER_ADMINISTRATOR,

    /** Carrier. */
    CARRIER
}
