package dev.ngb.backend.exception;

import dev.ngb.backend.exception.base.ConflictException;

import java.util.Map;

/** Signals that a profile update cannot claim a phone number owned by another account. */
public class PhoneNumberAlreadyUsedException extends ConflictException {

    /** Stable API code for a phone-number conflict. */
    public static final String CODE = "PHONE_NUMBER_ALREADY_USED";

    /**
     * Creates a conflict discovered by the database unique constraint during the update.
     *
     * @param phoneNumber normalized number that another account already owns
     * @param cause database constraint failure
     */
    public PhoneNumberAlreadyUsedException(String phoneNumber, Throwable cause) {
        super(
                CODE,
                "phone number is already used: " + phoneNumber,
                Map.of("phoneNumber", phoneNumber),
                cause);
    }
}
