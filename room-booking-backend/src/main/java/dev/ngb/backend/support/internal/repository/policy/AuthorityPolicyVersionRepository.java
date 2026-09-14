package dev.ngb.backend.support.internal.repository.policy;

import dev.ngb.backend.support.internal.model.policy.AuthorityPolicyVersion;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.policy.AuthorityPolicyVersion;


/**
 * Reads what a role may do, evaluated at execution time rather than at assignment time.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code authority_policy_versions}.</p>
 */
public interface AuthorityPolicyVersionRepository extends ListCrudRepository<AuthorityPolicyVersion, UUID> {

    /**
     * Finds the authority in force for a role in a market and legal entity.
     *
     * <pre>{@code
     * SELECT * FROM authority_policy_versions
     * WHERE authority_policy_key = :authorityPolicyKey
     *   AND role_code = :roleCode
     *   AND (market_id = :marketId OR market_id IS NULL)
     *   AND status = 'PUBLISHED'
     *   AND effective_from <= :at
     *   AND (effective_until IS NULL OR effective_until > :at)
     * ORDER BY market_id NULLS LAST, effective_from DESC
     * LIMIT 1
     * }</pre>
     *
     * @param authorityPolicyKey authority family
     * @param roleCode role being exercised
     * @param marketId market, or null for the global fallback
     * @param at instant to resolve at
     * @return the authority in force, when one is
     */
    @Query("""
            SELECT * FROM authority_policy_versions
            WHERE authority_policy_key = :authorityPolicyKey
              AND role_code = :roleCode
              AND (market_id = :marketId OR market_id IS NULL)
              AND status = 'PUBLISHED'
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
            ORDER BY market_id NULLS LAST, effective_from DESC
            LIMIT 1
            """)
    Optional<AuthorityPolicyVersion> findInForce(@Param("authorityPolicyKey") String authorityPolicyKey,
            @Param("roleCode") String roleCode, @Param("marketId") @Nullable UUID marketId,
            @Param("at") Instant at);
}
