package dev.ngb.backend.model;

/**
 * Where a version of the aspect vocabulary stands.
 */
public enum AspectTaxonomyStatus {
    /** Being written; its aspect set may still change. */
    DRAFT,
    /** In use. Frozen, aspects included. */
    ACTIVE,
    /** Superseded but still referenced by stored mentions. */
    DEPRECATED,
    /** No longer used for new extraction. */
    RETIRED
}
