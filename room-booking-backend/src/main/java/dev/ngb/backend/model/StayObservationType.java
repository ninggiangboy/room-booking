package dev.ngb.backend.model;

/**
 * How a stay observation was made.
 */
public enum StayObservationType {
    /** The guest said so. */
    GUEST_REPORT,
    /** The host said so. */
    HOST_ATTESTATION,
    /** An operator said so. */
    OPERATOR_ATTESTATION,
    /** An in-person registration record. */
    REGISTRATION_RECORD,
    /** A lock or access provider event. */
    ACCESS_EVENT,
    /** A device reading. */
    DEVICE_SIGNAL,
    /** A support agent verified it. */
    SUPPORT_VERIFICATION,
    /** An external service reported it. */
    PROVIDER_REPORT
}
