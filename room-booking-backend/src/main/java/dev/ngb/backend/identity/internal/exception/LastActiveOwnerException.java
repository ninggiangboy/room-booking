package dev.ngb.backend.identity.internal.exception;

import java.util.Map;
import java.util.UUID;

import dev.ngb.backend.platform.exception.base.BadRequestException;

/**
 * Signals that removing or demoting this member would leave the organization with no active
 * {@code OWNER}.
 *
 * <p>"At least one active owner" is a rule about the absence of other rows, so it cannot be a
 * database constraint; {@code OrganizationService} enforces it inside the transaction that would
 * break it, using {@code OrganizationMemberRepository#countActiveOwnersForUpdate} to serialize
 * concurrent attempts against the same organization.</p>
 */
public class LastActiveOwnerException extends BadRequestException {

    /** Stable API code for an attempt that would leave an organization without an active owner. */
    public static final String CODE = "LAST_ACTIVE_OWNER";

    /**
     * Creates a validation failure naming the organization that would be left ownerless.
     *
     * @param organizationId organization that would lose its last active owner
     */
    public LastActiveOwnerException(UUID organizationId) {
        super(
                CODE,
                "an organization must retain at least one active owner",
                Map.of("organizationId", organizationId));
    }
}
