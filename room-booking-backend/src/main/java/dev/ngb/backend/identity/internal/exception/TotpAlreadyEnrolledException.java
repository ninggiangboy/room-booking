package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.ConflictException;

/**
 * Signals that the caller already has an active TOTP credential.
 *
 * <p>Re-enrolling replaces a working factor with one the holder has not yet proven they can
 * produce codes for; the holder must disable the existing factor first.</p>
 */
public class TotpAlreadyEnrolledException extends ConflictException {

    /** Stable API code for a redundant TOTP enrollment. */
    public static final String CODE = "TOTP_ALREADY_ENROLLED";

    /**
     * Creates a conflict failure naming the account that already has an active factor.
     *
     * @param holderId account holder that already has an active TOTP credential
     */
    public TotpAlreadyEnrolledException(UUID holderId) {
        super(CODE, "an active TOTP credential already exists", Map.of("holderId", holderId));
    }
}
