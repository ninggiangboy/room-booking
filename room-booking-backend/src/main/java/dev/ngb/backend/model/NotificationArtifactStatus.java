package dev.ngb.backend.model;

/**
 * Approval lifecycle shared by notification policies and templates.
 *
 * <p>Approval is what freezes the artifact: from {@code APPROVED} onwards the content cannot be
 * edited, only retired in favour of a new version.</p>
 */
public enum NotificationArtifactStatus {
    /** Being written. */
    DRAFT,
    /** Submitted for approval. */
    IN_REVIEW,
    /** Signed off and frozen. */
    APPROVED,
    /** In use for new intents. */
    ACTIVE,
    /** Withdrawn from selection; existing evidence still cites it. */
    RETIRED
}
