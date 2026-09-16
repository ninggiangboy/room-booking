package dev.ngb.backend.identity.internal.exception;

import dev.ngb.backend.platform.exception.base.BadRequestException;

/**
 * Signals that a submitted contact-channel verification code is unknown, expired, already used, or
 * was issued for a different channel.
 *
 * <p>Carries no data map, matching {@link InvalidEmailVerificationTokenException}: naming which of
 * those reasons applied would tell a guesser how close their guess came.</p>
 */
public class InvalidContactChannelVerificationCodeException extends BadRequestException {

    /** Stable API code for a rejected contact-channel verification code. */
    public static final String CODE = "INVALID_CONTACT_CHANNEL_VERIFICATION_CODE";

    /** Creates a validation failure with no further detail. */
    public InvalidContactChannelVerificationCodeException() {
        super(CODE, "verification code is invalid or expired");
    }
}
