package dev.ngb.backend.model;

/**
 * Which domain owns the decision a monitoring index row points at.
 */
public enum DecisionDomain {

    /** Search and ranking. */
    DISCOVERY,

    /** What a guest is quoted and what a host nets. */
    PRICING,

    /** Reservation lifecycle. */
    BOOKING,

    /** Collection and refund. */
    PAYMENT,

    /** Fraud and abuse decisions. */
    RISK,

    /** Content and actor decisions. */
    MODERATION,

    /** Case handling and remedy. */
    SUPPORT,

    /** What is sent, to whom, and when. */
    MESSAGING,

    /** Review publication and aggregates. */
    REVIEW,

    /** Host money leaving the platform. */
    PAYOUT
}
