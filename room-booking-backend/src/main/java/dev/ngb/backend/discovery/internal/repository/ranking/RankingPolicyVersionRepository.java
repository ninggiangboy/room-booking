package dev.ngb.backend.discovery.internal.repository.ranking;

import dev.ngb.backend.discovery.internal.model.ranking.RankingPolicyVersion;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the approved ranking policy a search is served under.
 *
 * <p>Weights, caps, floors, and budgets are configuration with a version and an approver, not
 * constants in a service, so a rank served last week can still be explained.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code ranking_policy_versions}.</p>
 */
public interface RankingPolicyVersionRepository extends ListCrudRepository<RankingPolicyVersion, UUID> {

    /**
     * Finds the active policy for a market, falling back to the global one.
     *
     * <pre>{@code
     * SELECT * FROM ranking_policy_versions
     * WHERE policy_key = :policyKey
     *   AND (market_id = :marketId OR market_id IS NULL)
     *   AND status = 'ACTIVE'
     *   AND effective_from <= :at
     *   AND (effective_until IS NULL OR effective_until > :at)
     * ORDER BY market_id NULLS LAST, effective_from DESC
     * LIMIT 1
     * }</pre>
     *
     * <p>A market-specific version wins over the global fallback, which is why the ordering puts nulls
     * last rather than leaving the choice to whichever row the planner returned first.</p>
     *
     * @param policyKey policy family
     * @param marketId market, or null for the global fallback
     * @param at instant to resolve at
     * @return the policy in force, when one is
     */
    @Query("""
            SELECT * FROM ranking_policy_versions
            WHERE policy_key = :policyKey
              AND (market_id = :marketId OR market_id IS NULL)
              AND status = 'ACTIVE'
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
            ORDER BY market_id NULLS LAST, effective_from DESC
            LIMIT 1
            """)
    Optional<RankingPolicyVersion> findInForce(@Param("policyKey") String policyKey,
            @Param("marketId") @Nullable UUID marketId, @Param("at") Instant at);

    /**
     * Lists every version of one policy family, newest first, for the governance screen.
     *
     * @param policyKey policy family
     * @return possibly empty list, newest version first
     */
    List<RankingPolicyVersion> findByPolicyKeyOrderByPolicyVersionDesc(String policyKey);
}
