package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.BadRequestException;

/**
 * Signals that a delegation was rejected because it named a capability the organization does not
 * itself hold, or because it did not narrow the scope below {@code GLOBAL}.
 *
 * <p>A co-host may only be given a subset of the organization's own authority; this is the
 * enforcement of {@link dev.ngb.backend.identity.internal.model.capability.CapabilityGrant}'s class
 * documentation that a delegated grant must be "both stronger and narrower than a role" — narrower
 * in scope, never broader in capability.</p>
 */
public class DelegationExceedsAuthorityException extends BadRequestException {

    /** Stable API code for a delegation exceeding the organization's own authority. */
    public static final String CODE = "DELEGATION_EXCEEDS_AUTHORITY";

    /**
     * Creates a validation failure naming the organization and the rejected capability set.
     *
     * @param organizationId organization the delegation was attempted from
     * @param message specific reason the delegation was rejected
     */
    public DelegationExceedsAuthorityException(UUID organizationId, String message) {
        super(CODE, message, Map.of("organizationId", organizationId));
    }
}
