package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.ForbiddenException;

/**
 * Signals that the acting account holder is not an active {@code OWNER} or {@code ADMIN} of the
 * organization it tried to act on.
 *
 * <p>Organization authority is resource-scoped, not a global capability, so it is checked here
 * rather than by Spring Security's coarse {@code hasAuthority} gate: the same account holder is
 * {@code OWNER} of one organization and a stranger to another.</p>
 */
public class InsufficientOrganizationAuthorityException extends ForbiddenException {

    /** Stable API code for insufficient organization authority. */
    public static final String CODE = "INSUFFICIENT_ORGANIZATION_AUTHORITY";

    /**
     * Creates a forbidden failure naming the organization the caller lacked authority over.
     *
     * @param organizationId organization the caller tried to act on
     */
    public InsufficientOrganizationAuthorityException(UUID organizationId) {
        super(
                CODE,
                "an active OWNER or ADMIN membership is required for this action",
                Map.of("organizationId", organizationId));
    }
}
