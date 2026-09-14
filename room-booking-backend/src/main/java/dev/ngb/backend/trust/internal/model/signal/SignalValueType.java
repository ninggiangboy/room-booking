package dev.ngb.backend.trust.internal.model.signal;

/**
 * The shape of what an observation carries.
 *
 * <p>A check constraint makes each shape carry the column it claims, and {@code ABSENT} is a
 * recorded state rather than a blank row: missing evidence is unknown, not safe.</p>
 */
public enum SignalValueType {
    /** A normalized true or false. */
    BOOLEAN,
    /** A normalized category value. */
    CATEGORICAL,
    /** A numeric measurement. */
    NUMERIC,
    /** Minor units together with an ISO 4217 currency. */
    MONEY,
    /** A pointer to protected detail held elsewhere. */
    REFERENCE,
    /** The observation exists but carries no value. */
    ABSENT;
}
