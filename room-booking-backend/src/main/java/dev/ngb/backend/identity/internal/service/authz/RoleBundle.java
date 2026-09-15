package dev.ngb.backend.identity.internal.service.authz;

import java.util.Set;

/**
 * Named bundles of {@link Capability} conferred together by one role-labeled grant.
 *
 * <p>A {@link dev.ngb.backend.identity.internal.model.capability.CapabilityGrant} that carries a
 * {@code roleName} also stores the materialized capability array migration {@code 014} computed
 * from that role at backfill time. This enum is what expands the label back into capabilities at
 * evaluation time, so a bundle can change in one place instead of requiring every stored grant to
 * be rewritten. It replaces {@code internal.model.Role}, which could say only "has this role" and
 * had no way to express a listing-scoped or delegated grant.</p>
 */
public enum RoleBundle {

    /** Can search for and reserve rooms. */
    GUEST(Set.of(
            Capability.BOOKING_CREATE,
            Capability.BOOKING_CANCEL_OWN,
            Capability.REVIEW_WRITE_OWN,
            Capability.MESSAGE_SEND)),
    /** Can publish and manage listings. */
    HOST(Set.of(
            Capability.CAN_DRAFT,
            Capability.CAN_PUBLISH,
            Capability.CAN_ACCEPT_BOOKING,
            Capability.CAN_RECEIVE_PAYOUT,
            Capability.LISTING_MANAGE_OWN,
            Capability.CALENDAR_MANAGE_OWN,
            Capability.MESSAGE_SEND)),
    /** Platform operator with administrative capabilities. */
    ADMIN(Set.of(
            Capability.ACCOUNT_SUSPEND,
            Capability.ACCOUNT_REACTIVATE,
            Capability.CONFIGURATION_APPROVE,
            Capability.SUPPORT_CASE_MANAGE));

    private final Set<Capability> capabilities;

    RoleBundle(Set<Capability> capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * Returns the capabilities this bundle confers.
     *
     * @return immutable set of capabilities named by this role
     */
    public Set<Capability> capabilities() {
        return capabilities;
    }
}
