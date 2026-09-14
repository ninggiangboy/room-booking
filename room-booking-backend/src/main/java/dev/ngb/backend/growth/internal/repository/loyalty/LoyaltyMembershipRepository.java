package dev.ngb.backend.growth.internal.repository.loyalty;

import dev.ngb.backend.growth.internal.model.loyalty.LoyaltyMembership;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.growth.internal.model.loyalty.LoyaltyMembership;


/**
 * Reads where each member stands in a loyalty programme.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code loyalty_memberships}.</p>
 */
public interface LoyaltyMembershipRepository extends ListCrudRepository<LoyaltyMembership, UUID> {

    /**
     * Finds one person's membership of one programme.
     *
     * @param accountHolderId the member
     * @param growthProgramId the programme
     * @return the membership, when they have one
     */
    Optional<LoyaltyMembership> findByAccountHolderIdAndGrowthProgramId(UUID accountHolderId,
            UUID growthProgramId);

    /**
     * Lists every programme one person is a member of.
     *
     * @param accountHolderId the member
     * @return possibly empty list
     */
    List<LoyaltyMembership> findByAccountHolderId(UUID accountHolderId);

    /**
     * Reads one membership under a row lock, which a qualification run takes before accruing and
     * moving the tier: the accrual counters and the tier are updated together.
     *
     * <pre>{@code
     * SELECT * FROM loyalty_memberships WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an open transaction.</p>
     *
     * @param id the membership
     * @return the locked membership, when it exists
     */
    @Query("SELECT * FROM loyalty_memberships WHERE id = :id FOR UPDATE")
    Optional<LoyaltyMembership> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Lists the memberships due to be judged, which is the set the tier review takes.
     *
     * <pre>{@code
     * SELECT * FROM loyalty_memberships
     * WHERE membership_state = 'ACTIVE' AND tier_review_at <= :at
     * ORDER BY tier_review_at
     * }</pre>
     *
     * @param at instant the review is running for
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT * FROM loyalty_memberships
            WHERE membership_state = 'ACTIVE' AND tier_review_at <= :at
            ORDER BY tier_review_at
            """)
    List<LoyaltyMembership> findDueForReview(@Param("at") Instant at);

    /**
     * Counts the members holding each tier of one programme, which is what a benefit-cost estimate
     * is built on.
     *
     * <pre>{@code
     * SELECT current_tier_definition_id, count(*) AS member_count
     * FROM loyalty_memberships
     * WHERE growth_program_id = :growthProgramId AND membership_state = 'ACTIVE'
     * GROUP BY current_tier_definition_id
     * ORDER BY member_count DESC
     * }</pre>
     *
     * @param growthProgramId the programme
     * @return one row per tier, largest first
     */
    @Query("""
            SELECT current_tier_definition_id, count(*) AS member_count
            FROM loyalty_memberships
            WHERE growth_program_id = :growthProgramId AND membership_state = 'ACTIVE'
            GROUP BY current_tier_definition_id
            ORDER BY member_count DESC
            """)
    List<TierMemberCount> countByTier(@Param("growthProgramId") UUID growthProgramId);

    /**
     * How many members hold one tier.
     *
     * @param currentTierDefinitionId the tier
     * @param memberCount how many members hold it
     */
    record TierMemberCount(UUID currentTierDefinitionId, long memberCount) {}
}
