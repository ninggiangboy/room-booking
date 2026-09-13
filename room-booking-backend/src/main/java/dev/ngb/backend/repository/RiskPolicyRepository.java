package dev.ngb.backend.repository;

import dev.ngb.backend.model.RiskPolicy;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads policy versions.
 *
 * <p>An evaluation resolves the applicable version once, at the start, and records the epoch it
 * bound. An exclusion constraint guarantees the resolution below cannot return two overlapping live
 * versions of the same key.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_policies}.</p>
 */
public interface RiskPolicyRepository extends ListCrudRepository<RiskPolicy, UUID> {

    /**
     * Finds one policy version.
     *
     * <pre>{@code
     * SELECT * FROM risk_policies WHERE policy_key = :policyKey AND policy_version = :policyVersion
     * }</pre>
     *
     * @param policyKey policy key
     * @param policyVersion version
     * @return the version, when it exists
     */
    Optional<RiskPolicy> findByPolicyKeyAndPolicyVersion(String policyKey, int policyVersion);

    /**
     * Resolves the version of a policy in force at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_policies
     * WHERE policy_key = :policyKey
     *   AND status = 'ACTIVE'
     *   AND shadow_mode = FALSE
     *   AND effective_from <= :at
     *   AND (effective_until IS NULL OR effective_until > :at)
     * }</pre>
     *
     * <p>At most one row can come back: {@code ex_risk_policies_effective_overlap} refuses two live
     * versions of one key whose windows overlap, so the applicable rule is never a race.</p>
     *
     * @param policyKey policy key
     * @param at instant to resolve at
     * @return the version in force, when there is one
     */
    @Query("""
            SELECT *
            FROM risk_policies
            WHERE policy_key = :policyKey
              AND status = 'ACTIVE'
              AND shadow_mode = FALSE
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
            """)
    Optional<RiskPolicy> findEffective(@Param("policyKey") String policyKey, @Param("at") Instant at);

    /**
     * Lists the live versions that decide one protected action, in priority order.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_policies
     * WHERE status = 'ACTIVE'
     *   AND kill_switch_engaged = FALSE
     *   AND :actionKey = ANY (action_keys)
     *   AND effective_from <= :at
     *   AND (effective_until IS NULL OR effective_until > :at)
     * ORDER BY priority
     * }</pre>
     *
     * @param actionKey protected action
     * @param at instant to resolve at
     * @return possibly empty list, highest priority first
     */
    @Query("""
            SELECT *
            FROM risk_policies
            WHERE status = 'ACTIVE'
              AND kill_switch_engaged = FALSE
              AND :actionKey = ANY (action_keys)
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
            ORDER BY priority
            """)
    List<RiskPolicy> findLiveForAction(@Param("actionKey") String actionKey, @Param("at") Instant at);
}
