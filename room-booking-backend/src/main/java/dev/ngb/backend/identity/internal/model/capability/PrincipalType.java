package dev.ngb.backend.identity.internal.model.capability;

/**
 * Kind of principal a capability grant or restriction applies to.
 *
 * <p>Organizations hold capabilities in their own right, separately from the members who exercise
 * them, so that removing a member does not strip the organization's authority. Migration
 * {@code 037} renamed {@code USER} to {@code PERSON} once the legacy {@code users} table it named
 * was retired: a grantee is always an {@link
 * dev.ngb.backend.identity.internal.model.account.AccountHolder} now, and {@code PERSON} matches
 * {@link dev.ngb.backend.identity.internal.model.account.AccountHolderType#PERSON}.</p>
 */
public enum PrincipalType {
    /** An individual person's account holder. */
    PERSON,
    /** An organization account holder. */
    ORGANIZATION,
    /** An internal service identity. */
    SERVICE
}
