package dev.ngb.backend.analytics.types;

/**
 * What a deletion request does to a dataset.
 *
 * <p>Retention under a legal basis is a decision for the owning policy domain to record, not one
 * the analytics platform invents for itself.</p>
 */
public enum DeletionBehaviour {

    /** Rows for the subject are removed. */
    ERASE,

    /** Identifiers are replaced so the row survives without the subject. */
    PSEUDONYMIZE,

    /** Rows are retained but excluded from every read. */
    SUPPRESS,

    /** Retained because a contractual, financial, fraud, safety or legal obligation requires it; decided by the owning policy domain, not here. */
    RETAIN_UNDER_LEGAL_BASIS
}
