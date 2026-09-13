package dev.ngb.backend.model;

/**
 * How personal the data in a contract or dataset is.
 *
 * <p>The most restrictive applicable class follows the data downstream unless an approved
 * transformation says otherwise, and only the two least restrictive may be trained on.</p>
 */
public enum DataPrivacyClass {

    /** Carries nothing about an identifiable person. */
    NON_PERSONAL,

    /** Keyed by pseudonyms whose mapping is held separately. */
    PSEUDONYMOUS,

    /** Relates to an identifiable person. */
    PERSONAL,

    /** Personal and additionally limited to named purposes and consumers. */
    RESTRICTED
}
