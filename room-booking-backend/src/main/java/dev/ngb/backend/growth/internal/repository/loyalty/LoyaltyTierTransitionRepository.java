package dev.ngb.backend.growth.internal.repository.loyalty;

import dev.ngb.backend.growth.internal.model.loyalty.LoyaltyTierTransition;
import dev.ngb.backend.growth.internal.model.loyalty.LoyaltyTransitionReason;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.growth.internal.model.loyalty.LoyaltyTierTransition;
import dev.ngb.backend.growth.internal.model.loyalty.LoyaltyTransitionReason;


/**
 * Reads how each member arrived at the tier they hold.
 *
 * <p>Rows are append-only and carry the counts the change was judged on, which is what a member
 * disputing a downgrade is owed.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code loyalty_tier_transitions}.</p>
 */
public interface LoyaltyTierTransitionRepository extends ListCrudRepository<LoyaltyTierTransition, UUID> {

    /**
     * Lists one membership's tier history, newest first.
     *
     * @param loyaltyMembershipId the membership
     * @return possibly empty list, most recent first
     */
    List<LoyaltyTierTransition> findByLoyaltyMembershipIdOrderByEffectiveFromDesc(
            UUID loyaltyMembershipId);

    /**
     * Lists the tiers granted outside the published thresholds, which is the set a review of
     * discretionary benefits has to read.
     *
     * <pre>{@code
     * SELECT * FROM loyalty_tier_transitions
     * WHERE transition_reason IN ('MANUAL_GRANT', 'PARTNER_STATUS_MATCH')
     *   AND effective_from >= :since
     * ORDER BY effective_from DESC
     * }</pre>
     *
     * @param since instant to look back to
     * @return possibly empty list, most recent first
     */
    @Query("""
            SELECT * FROM loyalty_tier_transitions
            WHERE transition_reason IN ('MANUAL_GRANT', 'PARTNER_STATUS_MATCH')
              AND effective_from >= :since
            ORDER BY effective_from DESC
            """)
    List<LoyaltyTierTransition> findDiscretionary(@Param("since") Instant since);

    /**
     * Counts the reasons members changed tier in one period, which is how a programme quietly
     * downgrading everybody becomes visible.
     *
     * <pre>{@code
     * SELECT transition_reason, count(*) AS transition_count
     * FROM loyalty_tier_transitions
     * WHERE effective_from >= :from AND effective_from < :until
     * GROUP BY transition_reason
     * ORDER BY transition_count DESC
     * }</pre>
     *
     * @param from start of the period
     * @param until end of the period, exclusive
     * @return one row per reason, commonest first
     */
    @Query("""
            SELECT transition_reason, count(*) AS transition_count
            FROM loyalty_tier_transitions
            WHERE effective_from >= :from AND effective_from < :until
            GROUP BY transition_reason
            ORDER BY transition_count DESC
            """)
    List<TransitionReasonCount> countReasons(@Param("from") Instant from,
            @Param("until") Instant until);

    /**
     * How many tier changes happened for one reason.
     *
     * @param transitionReason why the tier changed
     * @param transitionCount how many changes had that reason
     */
    record TransitionReasonCount(LoyaltyTransitionReason transitionReason, long transitionCount) {}
}
