package dev.ngb.backend.trust.internal.repository.intervention;

import dev.ngb.backend.trust.internal.model.intervention.RiskRestriction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the governed interventions behind enforced limitations.
 *
 * <p>Enforcement composes every currently effective restriction on a target, so the query below is on
 * the hot path of every protected command and is bounded by a partial index rather than by lifetime
 * history.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_restrictions}.</p>
 */
public interface RiskRestrictionRepository extends ListCrudRepository<RiskRestriction, UUID> {

    /**
     * Reads the restrictions effective against a target at an instant.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_restrictions
     * WHERE target_subject_id = :targetSubjectId
     *   AND state IN ('ACTIVE', 'APPEAL_PENDING')
     *   AND effective_from <= :at
     *   AND (effective_until IS NULL OR effective_until > :at)
     * }</pre>
     *
     * <p>Compares the instant against the stored bounds rather than trusting the state column, so a late
     * expiry worker cannot accidentally leave a lapsed restriction in force.</p>
     *
     * @param targetSubjectId the restricted subject
     * @param at instant to evaluate at
     * @return possibly empty list
     */
    @Query("""
            SELECT *
            FROM risk_restrictions
            WHERE target_subject_id = :targetSubjectId
              AND state IN ('ACTIVE', 'APPEAL_PENDING')
              AND effective_from <= :at
              AND (effective_until IS NULL OR effective_until > :at)
            """)
    List<RiskRestriction> findEffectiveFor(@Param("targetSubjectId") UUID targetSubjectId,
                                           @Param("at") Instant at);

    /**
     * Finds the live restriction for one target, scope and intent.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_restrictions
     * WHERE target_subject_id = :targetSubjectId
     *   AND capability_scope = :capabilityScope
     *   AND policy_intent = :policyIntent
     *   AND resource_type IS NOT DISTINCT FROM :resourceType
     *   AND resource_id IS NOT DISTINCT FROM :resourceId
     *   AND state IN ('PROPOSED', 'ACTIVE', 'APPEAL_PENDING')
     * }</pre>
     *
     * <p>Matches {@code uk_risk_restrictions_live}, so at most one row can come back. Two rows expressing
     * one intent would be a double punishment whose lifting removes only half.</p>
     *
     * @param targetSubjectId the restricted subject
     * @param capabilityScope capability restricted
     * @param policyIntent why it exists
     * @param resourceType resource kind, or null
     * @param resourceId resource, or null
     * @return the live restriction, when there is one
     */
    @Query("""
            SELECT *
            FROM risk_restrictions
            WHERE target_subject_id = :targetSubjectId
              AND capability_scope = :capabilityScope
              AND policy_intent = :policyIntent
              AND resource_type IS NOT DISTINCT FROM :resourceType
              AND resource_id IS NOT DISTINCT FROM :resourceId
              AND state IN ('PROPOSED', 'ACTIVE', 'APPEAL_PENDING')
            """)
    Optional<RiskRestriction> findLive(@Param("targetSubjectId") UUID targetSubjectId,
                                       @Param("capabilityScope") String capabilityScope,
                                       @Param("policyIntent") String policyIntent,
                                       @Param("resourceType") String resourceType,
                                       @Param("resourceId") UUID resourceId);

    /**
     * Claims restrictions whose window has closed so they can be marked expired.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_restrictions
     * WHERE state IN ('ACTIVE', 'APPEAL_PENDING')
     *   AND effective_until IS NOT NULL
     *   AND effective_until <= :at
     * ORDER BY effective_until
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. The row's own end is what decides; a trigger refuses any update
     * that pushes it later, so a worker running late can close a restriction but never extend one.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum restrictions to claim
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM risk_restrictions
            WHERE state IN ('ACTIVE', 'APPEAL_PENDING')
              AND effective_until IS NOT NULL
              AND effective_until <= :at
            ORDER BY effective_until
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<RiskRestriction> claimExpired(@Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Lists restrictions due for review.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_restrictions
     * WHERE state IN ('ACTIVE', 'APPEAL_PENDING') AND review_due_at IS NOT NULL AND review_due_at <= :at
     * ORDER BY review_due_at
     * }</pre>
     *
     * @param at instant to treat as now
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT *
            FROM risk_restrictions
            WHERE state IN ('ACTIVE', 'APPEAL_PENDING')
              AND review_due_at IS NOT NULL
              AND review_due_at <= :at
            ORDER BY review_due_at
            """)
    List<RiskRestriction> findDueForReview(@Param("at") Instant at);
}
