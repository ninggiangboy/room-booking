package dev.ngb.backend.analytics.internal.model.arrival;

/**
 * What a consumer does with a field it does not recognise.
 *
 * <p>Stated in advance, because an unknown field never becomes a feature on its own.</p>
 */
public enum UnknownFieldPolicy {

    /** Skip fields the consumer does not recognise. */
    IGNORE,

    /** Hold the record aside for review. */
    QUARANTINE,

    /** Reject the record loudly. */
    FAIL
}
