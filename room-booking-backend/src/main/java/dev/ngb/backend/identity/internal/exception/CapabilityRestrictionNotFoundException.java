package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.NotFoundException;


/** Signals that no capability restriction exists for a requested identifier. */
public class CapabilityRestrictionNotFoundException extends NotFoundException {

    /** Stable API code for a missing capability restriction identifier. */
    public static final String CODE = "CAPABILITY_RESTRICTION_NOT_FOUND";

    /**
     * Creates a not-found failure for one restriction identifier.
     *
     * @param restrictionId missing restriction identifier
     */
    public CapabilityRestrictionNotFoundException(UUID restrictionId) {
        super(CODE, "capability restriction not found", Map.of("restrictionId", restrictionId));
    }
}
