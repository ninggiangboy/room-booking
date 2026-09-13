package dev.ngb.backend.model;

/**
 * The decision basis of {@code case_remedies}.
 */
public enum RemedyDecisionBasis {

    /** Amount due under the accepted booking or cancellation terms, or mandatory legal policy. */
    CONTRACTUAL,

    /** Amount due under an applicable protection programme or policy. */
    PROTECTION_OR_CLAIM,

    /** Discretionary service recovery that does not rewrite contract liability. */
    GOODWILL
}
