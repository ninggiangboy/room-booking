package dev.ngb.backend.trust.internal.model.content;

/**
 * Whether the content still exists in its own domain.
 *
 * <p>Deletion by an author does not erase a moderation history. Where lawful evidence must remain it
 * is restricted and pseudonymized rather than represented as fully deleted.</p>
 */
public enum ContentItemLifecycle {
    /** Present and readable in its own domain. */
    ACTIVE,
    /** Withdrawn from view by its author. */
    WITHDRAWN,
    /** Deleted at the author's request. */
    DELETED_BY_OWNER,
    /** Kept under a purpose that outlives the author's deletion. */
    RETAINED_EVIDENCE;
}
