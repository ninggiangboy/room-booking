package dev.ngb.backend.identity.internal.exception;

import dev.ngb.backend.platform.exception.base.UnauthorizedException;

/**
 * Signals that a sensitive action requires a fresh step-up proof that the request did not supply,
 * distinct from {@link InvalidStepUpProofException} so a client can tell "prompt for a code" apart
 * from "the code you sent was wrong."
 */
public class StepUpRequiredException extends UnauthorizedException {

    /** Stable API code for a missing step-up proof. */
    public static final String CODE = "STEP_UP_REQUIRED";

    /** Creates the failure for a holder with an enrolled second factor but no proof presented. */
    public StepUpRequiredException() {
        super(CODE, "a fresh step-up proof is required for this action");
    }
}
