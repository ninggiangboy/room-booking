package dev.ngb.backend.model;

/**
 * Review state of a localized content version.
 *
 * <p>Only {@code APPROVED} content is renderable, and the database refuses an approved row that
 * names no reviewer: content presenting a legal disclosure without an accountable reviewer is
 * exactly the content that must not reach a guest.</p>
 */
public enum ContentLifecycle {
    /** Authored, not yet submitted. */
    DRAFT,
    /** Submitted and awaiting review. */
    IN_REVIEW,
    /** Reviewed and renderable. */
    APPROVED,
    /** Replaced by a newer version; retained so history renders as it did. */
    RETIRED
}
