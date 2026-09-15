package dev.ngb.backend.trust.internal.model.intervention;

/**
 * Where a restriction stands.
 *
 * <p>Once in force, what the restriction is about is frozen and its end may be brought forward but
 * never pushed back: a late expiry worker extending a restriction is indistinguishable from a
 * punishment nobody decided.</p>
 */
public enum RestrictionState {
    /** Decided but not yet in force. */
    PROPOSED,
    /** In force. */
    ACTIVE,
    /** In force while an appeal is heard. */
    APPEAL_PENDING,
    /** Its window closed. */
    EXPIRED,
    /** Lifted, with a reason and a named revoker. */
    REVOKED,
    /** Replaced by another restriction naming it. */
    SUPERSEDED
}
