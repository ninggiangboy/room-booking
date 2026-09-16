package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.NotFoundException;

/**
 * Signals that no account holder with the given identifier is an organization.
 */
public class OrganizationNotFoundException extends NotFoundException {

    /** Stable API code for an absent or non-organization account holder. */
    public static final String CODE = "ORGANIZATION_NOT_FOUND";

    /**
     * Creates a not-found failure naming the requested organization.
     *
     * @param organizationId identifier that did not resolve to an organization
     */
    public OrganizationNotFoundException(UUID organizationId) {
        super(CODE, "no organization with that id", Map.of("organizationId", organizationId));
    }
}
