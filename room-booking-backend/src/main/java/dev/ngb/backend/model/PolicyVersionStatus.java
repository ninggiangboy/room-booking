package dev.ngb.backend.model;

/**
 * How far an effective-dated policy version has progressed.
 *
 * <p>Shared by the review policy and the reputation purpose registries. Published versions are frozen
 * by trigger, because rows elsewhere name the version they were written under.</p>
 */
public enum PolicyVersionStatus {
    /** Being written; nothing depends on it. */
    DRAFT,
    /** Approved but not yet in force. */
    APPROVED,
    /** In force. Frozen from here on. */
    PUBLISHED,
    /** A later version replaced it; kept because history points at it. */
    SUPERSEDED,
    /** Withdrawn without a replacement. */
    RETIRED
}
