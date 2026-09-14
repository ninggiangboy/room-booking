package dev.ngb.backend.stay.internal.model.incident;

/**
 * Which domain an incident was handed to.
 */
public enum IncidentTransferDomain {
    /** The support case domain. */
    SUPPORT,
    /** Trust and safety. */
    TRUST,
    /** Damage claims. */
    CLAIMS
}
