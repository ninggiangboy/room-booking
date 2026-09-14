package dev.ngb.backend.analytics.internal.model;

/**
 * Lifecycle of a governed definition that consumers read.
 *
 * <p>Leaving DRAFT freezes the meaning; a semantic change makes a new version. RETIRED is
 * terminal.</p>
 */
public enum DataContractStatus {

    /** Being written; freely editable. */
    DRAFT,

    /** Accepted but not yet published. */
    REVIEWED,

    /** The version consumers read by default. */
    CURRENT,

    /** Still readable while consumers migrate. */
    DEPRECATED,

    /** No longer available; terminal. */
    RETIRED
}
