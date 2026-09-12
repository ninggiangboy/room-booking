package dev.ngb.backend.repository;

import dev.ngb.backend.model.ReviewPolicyVersion;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the effective-dated rules reviews are judged by.
 *
 * <p>A cycle resolves its policy once, at the moment it opens, and stores the identifier. Nothing
 * looks the policy up again later, because a historical deadline must not move.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_policy_versions}.</p>
 */
public interface ReviewPolicyVersionRepository extends ListCrudRepository<ReviewPolicyVersion, UUID> {

    /**
     * Finds the rules currently in force for a market.
     *
     * <pre>{@code
     * SELECT *
     * FROM review_policy_versions
     * WHERE policy_key = :policyKey
     *   AND status = 'PUBLISHED'
     *   AND effective_from <= :at
     *   AND (effective_until IS NULL OR effective_until > :at)
     *   AND (market_code = :marketCode OR market_code IS NULL)
     * ORDER BY market_code NULLS LAST
     * LIMIT 1
     * }</pre>
     *
     * <p>A market-specific version wins over the platform default, which is what the ordering does. The
     * instant is bound by the caller because an index predicate may not read the clock.</p>
     *
     * @param policyKey policy to resolve
     * @param marketCode market the booking falls under
     * @param at instant to resolve at
     * @return the governing version, when one is in force
     */
    @Query("""
            SELECT *
            FROM review_policy_versions
            WHERE policy_key = :policyKey
              AND status = 'PUBLISHED'
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
              AND (market_code = :marketCode OR market_code IS NULL)
            ORDER BY market_code NULLS LAST
            LIMIT 1
            """)
    Optional<ReviewPolicyVersion> findGoverning(@Param("policyKey") String policyKey,
            @Param("marketCode") String marketCode, @Param("at") Instant at);

    /**
     * Finds one numbered version of a policy.
     *
     * <p>Spring derives {@code WHERE policy_key = ? AND policy_version = ?}, matching
     * {@code uk_review_policy_versions_identity}.</p>
     *
     * @param policyKey policy
     * @param policyVersion version within it
     * @return the version, when it exists
     */
    Optional<ReviewPolicyVersion> findByPolicyKeyAndPolicyVersion(String policyKey, int policyVersion);
}
