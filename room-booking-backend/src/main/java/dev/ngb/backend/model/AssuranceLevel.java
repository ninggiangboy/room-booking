package dev.ngb.backend.model;

/**
 * Strength of the authentication behind a session.
 *
 * <p>Sensitive commands — changing a payout destination, issuing a refund, altering security
 * settings — require a minimum level, and a session below it must prove more before proceeding
 * rather than being refused outright.</p>
 */
public enum AssuranceLevel {
    /** Single factor. */
    AAL1,
    /** Multi-factor. */
    AAL2,
    /** Hardware-backed multi-factor. */
    AAL3
}
