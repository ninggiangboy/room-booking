package dev.ngb.backend.model;

/**
 * How long an audit row is retained.
 *
 * <p>Every class except {@code PERMANENT} requires an explicit {@code retainUntil} instant, and
 * {@code PERMANENT} forbids one. Retention is decided when the row is written, because the policy
 * in force at that moment is the one that governs the evidence.</p>
 */
public enum RetentionClass {
    /** Operational evidence with a short life. */
    SHORT,
    /** The default business-record retention period. */
    STANDARD,
    /** Extended retention required by tax, finance, or regulatory rules. */
    EXTENDED,
    /** Retained indefinitely; no expiry instant is recorded. */
    PERMANENT
}
