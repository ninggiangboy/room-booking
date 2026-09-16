package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.NotFoundException;

/**
 * Signals that the caller has no active TOTP credential to verify a code against or disable.
 */
public class NoActiveTotpCredentialException extends NotFoundException {

    /** Stable API code for an absent TOTP credential. */
    public static final String CODE = "NO_ACTIVE_TOTP_CREDENTIAL";

    /**
     * Creates a not-found failure naming the account with no active factor.
     *
     * @param holderId account holder with no active TOTP credential
     */
    public NoActiveTotpCredentialException(UUID holderId) {
        super(CODE, "no active TOTP credential is enrolled", Map.of("holderId", holderId));
    }
}
