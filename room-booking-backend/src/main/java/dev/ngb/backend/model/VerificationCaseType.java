package dev.ngb.backend.model;

/**
 * Which question a verification case is trying to answer.
 *
 * <p>Cases are separated by question rather than bundled, so re-verifying an address does not reopen
 * an identity check that already passed, and only one live case of each kind may exist per profile.</p>
 */
public enum VerificationCaseType {
    /** Whether a natural person is who they claim to be. */
    IDENTITY,
    /** Whether a company exists and is registered as claimed. */
    BUSINESS,
    /** Whether a real person is present, rather than a photograph of a document. */
    LIVENESS,
    /** Whether the declared address is genuine. */
    ADDRESS,
    /** Whether the host owns the payout destination they nominated. */
    PAYOUT_OWNERSHIP,
    /** A periodic re-check of evidence that has gone stale. */
    REFRESH
}
