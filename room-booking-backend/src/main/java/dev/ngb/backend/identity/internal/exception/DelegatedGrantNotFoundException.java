package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.NotFoundException;

/**
 * Signals that no grant delegated by this organization matches the requested identifier.
 *
 * <p>Deliberately does not distinguish "belongs to a different organization" from "does not
 * exist", for the same enumeration reason {@link ContactChannelNotFoundException} gives.</p>
 */
public class DelegatedGrantNotFoundException extends NotFoundException {

    /** Stable API code for an absent or not-owned delegated grant. */
    public static final String CODE = "DELEGATED_GRANT_NOT_FOUND";

    /**
     * Creates a not-found failure naming the requested grant.
     *
     * @param grantId grant identifier that did not resolve
     */
    public DelegatedGrantNotFoundException(UUID grantId) {
        super(CODE, "no grant delegated by this organization with that id", Map.of("grantId", grantId));
    }
}
