package dev.ngb.backend.repository;

import dev.ngb.backend.model.NotificationPolicy;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the rules that turn committed facts into notices.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code notification_policies}.</p>
 */
public interface NotificationPolicyRepository extends ListCrudRepository<NotificationPolicy, UUID> {

    /**
     * Finds the policy that governs an event in a market.
     *
     * <pre>{@code
     * SELECT *
     * FROM notification_policies
     * WHERE source_event_type = :sourceEventType
     *   AND purpose_code = :purposeCode
     *   AND status = 'ACTIVE'
     *   AND (market_code = :marketCode OR market_code IS NULL)
     *   AND effective_from <= :at
     *   AND (effective_until IS NULL OR effective_until > :at)
     * ORDER BY market_code NULLS LAST
     * LIMIT 1
     * }</pre>
     *
     * <p>A market-specific version wins over the global one, which is what the ordering is for. The
     * instant is bound by the caller so that replaying an old event selects the policy that applied
     * then.</p>
     *
     * @param sourceEventType committed event type
     * @param purposeCode purpose being evaluated
     * @param marketCode market whose rules apply
     * @param at instant the policy must be effective at
     * @return the governing policy version, when one exists
     */
    @Query("""
            SELECT *
            FROM notification_policies
            WHERE source_event_type = :sourceEventType
              AND purpose_code = :purposeCode
              AND status = 'ACTIVE'
              AND (market_code = :marketCode OR market_code IS NULL)
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
            ORDER BY market_code NULLS LAST
            LIMIT 1
            """)
    Optional<NotificationPolicy> findGoverning(@Param("sourceEventType") String sourceEventType,
                                               @Param("purposeCode") String purposeCode,
                                               @Param("marketCode") String marketCode,
                                               @Param("at") Instant at);

    /**
     * Finds one version of a policy family.
     *
     * <p>Spring derives {@code WHERE policy_key = ? AND policy_version = ?}, matching
     * {@code uk_notification_policies_version}.</p>
     *
     * @param policyKey policy family
     * @param policyVersion version within it
     * @return the version, when it exists
     */
    Optional<NotificationPolicy> findByPolicyKeyAndPolicyVersion(String policyKey, Integer policyVersion);
}
