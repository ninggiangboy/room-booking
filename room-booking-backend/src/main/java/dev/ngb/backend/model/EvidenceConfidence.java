package dev.ngb.backend.model;

/**
 * How much weight a piece of evidence can carry.
 *
 * <p>{@code UNKNOWN} is the default, because no single optional interaction is conclusive.</p>
 */
public enum EvidenceConfidence {
    /** Weak or easily fabricated. */
    LOW,
    /** Supporting but not decisive. */
    MEDIUM,
    /** Strong and hard to fabricate. */
    HIGH,
    /** Not assessed. */
    UNKNOWN
}
