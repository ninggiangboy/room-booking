package dev.ngb.backend.model;

/**
 * Whether a feature definition may be used.
 *
 * <p>Approved definitions are frozen by trigger: a decision replayed a year later must apply the
 * meaning that was in force then, which is only possible if the meaning did not change.</p>
 */
public enum FeatureDefinitionStatus {
    /** Being designed; not yet usable. */
    DRAFT,
    /** In force and frozen. */
    APPROVED,
    /** Still readable for replay, not used for new evaluations. */
    DEPRECATED,
    /** No longer computed. */
    RETIRED;
}
