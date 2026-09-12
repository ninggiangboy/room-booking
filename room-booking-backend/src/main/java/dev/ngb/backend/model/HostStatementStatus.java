package dev.ngb.backend.model;

/**
 * How far a host statement has got, and whether it can still change.
 *
 * <p>An issued statement is immutable. A correction is a new version that links back, so a host who
 * downloaded a copy and a host who reloads the page are looking at the same numbers.</p>
 */
public enum HostStatementStatus {
    /** Being assembled. */
    DRAFT,
    /** Composed and rendered, not yet shown to the host. */
    GENERATED,
    /** Shown to the host and frozen. */
    ISSUED,
    /** Rendering failed; retryable from the same data hash. */
    FAILED,
    /** A later version corrects it; this one remains visible as history. */
    SUPERSEDED_BY_CORRECTION
}
