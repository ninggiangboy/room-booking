package dev.ngb.backend.trust.internal.model.intervention;

/**
 * What somebody did to protected risk evidence.
 *
 * <p>Migration 012's audit stream records what changed. This records what was read, which is the
 * half that matters when the abuse is a reviewer browsing rather than a reviewer acting.</p>
 */
public enum RiskAccessKind {
    /** One record was looked at. */
    VIEW,
    /** Records left the system; needs an approval reference and a count. */
    EXPORT,
    /** A record was written. */
    CHANGE,
    /** A search was run across records. */
    QUERY;
}
