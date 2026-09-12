package dev.ngb.backend.model;

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
