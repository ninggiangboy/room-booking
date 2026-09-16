package dev.ngb.backend.identity.internal.repository.account;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.identity.internal.model.account.OrganizationMember;
import dev.ngb.backend.identity.internal.model.account.OrganizationMemberStatus;


/**
 * Reads and maintains who belongs to an organization.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code organization_members}. Removal is a status change, so actions a member
 * took while active stay attributable.</p>
 */
public interface OrganizationMemberRepository
        extends ListCrudRepository<OrganizationMember, UUID> {

    /**
     * Finds one person's membership of one organization.
     *
     * <p>Spring derives {@code WHERE organization_id = ? AND member_holder_id = ?}, matching the
     * {@code uk_organization_members_pair} unique constraint.</p>
     *
     * @param organizationId organization in question
     * @param memberHolderId person in question
     * @return the membership when one exists, in any status
     */
    Optional<OrganizationMember> findByOrganizationIdAndMemberHolderId(
            UUID organizationId, UUID memberHolderId);

    /**
     * Returns an organization's members in one status.
     *
     * <p>Spring derives {@code WHERE organization_id = ? AND status = ?}.</p>
     *
     * @param organizationId organization being listed
     * @param status status to filter by
     * @return possibly empty list of memberships
     */
    List<OrganizationMember> findAllByOrganizationIdAndStatus(
            UUID organizationId,
            OrganizationMemberStatus status);

    /**
     * Counts the organization's remaining active owners, holding them for the transaction.
     *
     * <pre>{@code
     * SELECT count(*)
     * FROM organization_members
     * WHERE organization_id = :organizationId
     *   AND member_role = 'OWNER'
     *   AND status = 'ACTIVE'
     * FOR UPDATE
     * }</pre>
     *
     * <p><strong>Must be called inside the transaction that changes ownership.</strong> "At least
     * one active owner" is a rule about the absence of other rows, so no row constraint can express
     * it; two concurrent transactions each removing a different owner would each see one remaining
     * and leave the organization with none. Locking the owner rows is what serializes them.</p>
     *
     * @param organizationId organization being checked
     * @return number of active owners, with those rows locked
     */
    @Query("""
            SELECT count(*)
            FROM organization_members
            WHERE organization_id = :organizationId
              AND member_role = 'OWNER'
              AND status = 'ACTIVE'
            FOR UPDATE
            """)
    long countActiveOwnersForUpdate(@Param("organizationId") UUID organizationId);

    /**
     * Returns every organization a person belongs to in one status.
     *
     * <p>Spring derives {@code WHERE member_holder_id = ? AND status = ?}. Backs the organization
     * switcher a session uses to select its acting context.</p>
     *
     * @param memberHolderId person whose memberships are listed
     * @param status status to filter by
     * @return possibly empty list of memberships
     */
    List<OrganizationMember> findAllByMemberHolderIdAndStatus(
            UUID memberHolderId,
            OrganizationMemberStatus status);

    /**
     * Returns every membership a person holds that has not been removed, active and pending
     * invitations alike.
     *
     * <p>Spring derives {@code WHERE member_holder_id = ? AND status != 'REMOVED'}. Backs a
     * "my organizations" view that must show both, distinguished by {@code status} in the
     * response, rather than two separate calls.</p>
     *
     * @param memberHolderId person whose memberships are listed
     * @return possibly empty list of the person's non-removed memberships
     */
    List<OrganizationMember> findAllByMemberHolderIdAndStatusNot(
            UUID memberHolderId, OrganizationMemberStatus status);
}
