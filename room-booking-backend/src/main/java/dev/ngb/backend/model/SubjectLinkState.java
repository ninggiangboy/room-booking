package dev.ngb.backend.model;

/**
 * Where a link stands.
 *
 * <p>Suppression is terminal. Re-linking the same subject means a new row with a new consent and a
 * new interval, which leaves the original request visible.</p>
 */
public enum SubjectLinkState {

    /** In force. */
    ACTIVE,

    /** Withdrawn but still on the record. */
    REVOKED,

    /** Removed under an erasure request; terminal. */
    SUPPRESSED
}
