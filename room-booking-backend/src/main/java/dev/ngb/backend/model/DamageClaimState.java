package dev.ngb.backend.model;

/**
 * Where a damage claim stands.
 *
 * <p>Claim status does not overwrite case, payment, ledger, recovery, provider or appeal status.</p>
 */
public enum DamageClaimState {

    /** Draft. */
    DRAFT,

    /** Submitted. */
    SUBMITTED,

    /** Eligibility review. */
    ELIGIBILITY_REVIEW,

    /** Ineligible. */
    INELIGIBLE,

    /** Evidence collection. */
    EVIDENCE_COLLECTION,

    /** Respondent review. */
    RESPONDENT_REVIEW,

    /** Adjudication. */
    ADJUDICATION,

    /** Decided. */
    DECIDED,

    /** Payment pending. */
    PAYMENT_PENDING,

    /** Recovery pending. */
    RECOVERY_PENDING,

    /** Appealed. */
    APPEALED,

    /** Settled. */
    SETTLED,

    /** Closed. */
    CLOSED,

    /** Withdrawn. */
    WITHDRAWN
}
