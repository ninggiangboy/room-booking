package dev.ngb.backend.model;

/**
 * Where a reason code stands in its approval lifecycle.
 *
 * <p>Only an active code may be shown to a guest.</p>
 */
public enum ReasonCodeStatus {

    /** Draft. */
    DRAFT,

    /** Active. */
    ACTIVE,

    /** Suspended. */
    SUSPENDED,

    /** Retired. */
    RETIRED
}
