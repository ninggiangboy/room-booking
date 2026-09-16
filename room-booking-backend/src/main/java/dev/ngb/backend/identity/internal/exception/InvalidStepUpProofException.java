package dev.ngb.backend.identity.internal.exception;

import dev.ngb.backend.platform.exception.base.BadRequestException;

/**
 * Signals that a submitted step-up proof token is unknown, expired, already consumed, or belongs
 * to a different account holder than the one presenting it.
 *
 * <p>Carries no data map, matching {@link InvalidTotpCodeException} and {@link
 * InvalidContactChannelVerificationCodeException}.</p>
 */
public class InvalidStepUpProofException extends BadRequestException {

    /** Stable API code for a rejected step-up proof. */
    public static final String CODE = "INVALID_STEP_UP_PROOF";

    /** Creates a validation failure with no further detail. */
    public InvalidStepUpProofException() {
        super(CODE, "step-up proof is invalid or expired");
    }
}
