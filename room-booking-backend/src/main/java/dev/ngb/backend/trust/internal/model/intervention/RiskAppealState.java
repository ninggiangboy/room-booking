package dev.ngb.backend.trust.internal.model.intervention;

/**
 * Where an appeal stands.
 *
 * <p>Intake acknowledges receipt without promising reversal, which is why acknowledgement is its own
 * state. Deciding requires findings, and granting requires a prospective restoration date.</p>
 */
public enum RiskAppealState {
    /** Received. */
    SUBMITTED,
    /** Receipt confirmed to the appellant. */
    ACKNOWLEDGED,
    /** Being heard. */
    IN_REVIEW,
    /** Answered, with findings. */
    DECIDED,
    /** Withdrawn by the appellant. */
    WITHDRAWN,
    /** The window closed without a decision. */
    EXPIRED
}
