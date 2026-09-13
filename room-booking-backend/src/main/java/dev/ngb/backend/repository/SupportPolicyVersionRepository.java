package dev.ngb.backend.repository;

import dev.ngb.backend.model.SupportPolicyVersion;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the effective-dated support policy packages.
 *
 * <p>A decision selects a version once and stores it, so these queries serve selection rather than
 * evaluation: nothing reads the current package to re-explain a decision already taken.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code support_policy_versions}.</p>
 */
public interface SupportPolicyVersionRepository extends ListCrudRepository<SupportPolicyVersion, UUID> {

    /**
     * Finds the package in force for a market at an instant.
     *
     * <pre>{@code
     * SELECT * FROM support_policy_versions
     * WHERE policy_key = :policyKey
     *   AND (market_id = :marketId OR market_id IS NULL)
     *   AND status = 'PUBLISHED'
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
     * @return the package in force, when one is
     */
    @Query("""
            SELECT * FROM support_policy_versions
            WHERE policy_key = :policyKey
              AND (market_id = :marketId OR market_id IS NULL)
              AND status = 'PUBLISHED'
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
            ORDER BY market_id NULLS LAST, effective_from DESC
            LIMIT 1
            """)
    Optional<SupportPolicyVersion> findInForce(@Param("policyKey") String policyKey,
            @Param("marketId") @Nullable UUID marketId, @Param("at") Instant at);

    /**
     * Lists every version of one policy family, newest first, for the governance screen.
     *
     * @param policyKey policy family
     * @return possibly empty list, newest version first
     */
    List<SupportPolicyVersion> findByPolicyKeyOrderByPolicyVersionDesc(String policyKey);
}
