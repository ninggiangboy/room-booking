package dev.ngb.backend.model;

/**
 * Which rung of the entitlement ladder a decision stopped on.
 *
 * <p>Safety and legal constraints come first, then the accepted contract, mandatory market obligations,
 * protection terms, marketplace terms, service recovery, and authorized exceptional review last.</p>
 */
public enum PrecedenceRung {

    /** Immediate safety, sanctions, privacy and court or regulator constraints. */
    SAFETY_LEGAL,

    /** The accepted booking contract and ordinary cancellation entitlement. */
    ACCEPTED_CONTRACT,

    /** Consumer or accommodation obligations the market imposes. */
    MANDATORY_MARKET_OBLIGATION,

    /** An applicable protection or insurance contract and its exclusions. */
    PROTECTION_CONTRACT,

    /** Host and guest marketplace terms and documented liability allocation. */
    MARKETPLACE_TERMS,

    /** Approved service-recovery or goodwill policy. */
    SERVICE_RECOVERY,

    /** An authorized exception, which must name its approved code. */
    EXCEPTIONAL_MANUAL_REVIEW
}
