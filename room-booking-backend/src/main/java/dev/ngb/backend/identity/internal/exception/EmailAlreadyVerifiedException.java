package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import dev.ngb.backend.identity.internal.model.account.AccountHolder;
import dev.ngb.backend.identity.internal.model.account.ContactChannel;

import dev.ngb.backend.platform.exception.base.ConflictException;


/** Signals that an account does not need another email-verification token. */
public class EmailAlreadyVerifiedException extends ConflictException {

    /** Stable API code for an unnecessary verification request. */
    public static final String CODE = "EMAIL_ALREADY_VERIFIED";

    /**
     * Creates a conflict containing safe account context.
     *
     * @param holder account holder whose email is already verified
     * @param emailChannel the holder's already-verified primary email channel
     */
    public EmailAlreadyVerifiedException(AccountHolder holder, ContactChannel emailChannel) {
        super(
                CODE,
                "email is already verified",
                Map.of("userId", holder.getId(), "email", emailChannel.getNormalizedValue()));
    }
}
