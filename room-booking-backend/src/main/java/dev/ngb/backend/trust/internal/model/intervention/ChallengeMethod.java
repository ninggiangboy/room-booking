package dev.ngb.backend.trust.internal.model.intervention;

/**
 * What kind of proof a challenge asks for.
 *
 * <p>Each method has different security and retention rules. No raw secret is stored against a
 * challenge; what is kept is a reference to the evidence that it was satisfied.</p>
 */
public enum ChallengeMethod {
    /** A code sent to a verified email address. */
    EMAIL_VERIFICATION,
    /** A code sent to a verified phone number. */
    PHONE_VERIFICATION,
    /** A second authentication factor. */
    MFA,
    /** The account password, entered again. */
    PASSWORD_REENTRY,
    /** An identity document check. */
    IDENTITY_DOCUMENT,
    /** A liveness check. */
    LIVENESS,
    /** Confirmation of an instrument the actor controls. */
    PAYMENT_CONFIRMATION,
    /** Issuer authentication of a card payment. */
    THREE_D_SECURE,
    /** Evidence assessed by a person. */
    MANUAL_PROOF
}
