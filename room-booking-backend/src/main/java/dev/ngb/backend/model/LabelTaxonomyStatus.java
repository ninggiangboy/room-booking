package dev.ngb.backend.model;

/**
 * Whether a label vocabulary is in use.
 *
 * <p>Frozen once active by trigger: a model trained last year must be able to say which vocabulary
 * its ground truth was written in.</p>
 */
public enum LabelTaxonomyStatus {
    /** Being defined. */
    DRAFT,
    /** In use and frozen. */
    ACTIVE,
    /** Readable for replay, not used for new labels. */
    DEPRECATED,
    /** Withdrawn. */
    RETIRED;
}
