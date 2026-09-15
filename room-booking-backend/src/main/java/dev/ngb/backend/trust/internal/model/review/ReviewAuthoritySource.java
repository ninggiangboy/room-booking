package dev.ngb.backend.trust.internal.model.review;

/**
 * Under what authority a reviewer acted.
 */
public enum ReviewAuthoritySource {
    /** The skill the queue requires. */
    QUEUE_SKILL,
    /** An escalation path. */
    ESCALATION,
    /** A break-glass grant, which earns a post-use review. */
    BREAK_GLASS,
    /** An appeal panel. */
    APPEAL_PANEL,
    /** A quality sampling authority. */
    QUALITY_ASSURANCE
}
