package dev.ngb.backend.support.internal.model.evidence;

/**
 * The redaction reason of {@code evidence_redactions}.
 */
public enum RedactionReason {

    /** Other party personal data. */
    OTHER_PARTY_PERSONAL_DATA,

    /** Identity document. */
    IDENTITY_DOCUMENT,

    /** Payment data. */
    PAYMENT_DATA,

    /** Access secret. */
    ACCESS_SECRET,

    /** Exact address. */
    EXACT_ADDRESS,

    /** Health data. */
    HEALTH_DATA,

    /** Minor present. */
    MINOR_PRESENT,

    /** Legal privilege. */
    LEGAL_PRIVILEGE,

    /** Provider minimization. */
    PROVIDER_MINIMIZATION
}
