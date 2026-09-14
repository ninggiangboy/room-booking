package dev.ngb.backend.trust.internal.repository.decision;

import dev.ngb.backend.trust.internal.model.decision.RiskDecision;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.trust.internal.model.decision.RiskDecision;


/**
 * Reads decisions.
 *
 * <p>The identity lookup is what a retry uses: one evaluation has one answer, so a caller that lost
 * its response reads the committed decision instead of taking a second one.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_decisions}.</p>
 */
public interface RiskDecisionRepository extends ListCrudRepository<RiskDecision, UUID> {

    /**
     * Finds the decision already taken for one canonical evaluation identity.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_decisions
     * WHERE protected_action = :protectedAction
     *   AND actor_subject_id = :actorSubjectId
     *   AND resource_type IS NOT DISTINCT FROM :resourceType
     *   AND resource_id IS NOT DISTINCT FROM :resourceId
     *   AND command_id IS NOT DISTINCT FROM :commandId
     *   AND policy_epoch = :policyEpoch
     * }</pre>
     *
     * <p>Matches {@code uk_risk_decisions_evaluation}, which treats nulls as equal, so an action with no
     * resource and no command still collapses to one answer rather than to an unlimited number of rows
     * that all look unique because something was null.</p>
     *
     * @param protectedAction registered action
     * @param actorSubjectId who proposed it
     * @param resourceType resource kind, or null
     * @param resourceId resource, or null
     * @param commandId domain command, or null
     * @param policyEpoch configuration set bound by the evaluation
     * @return the committed decision, when one exists
     */
    @Query("""
            SELECT *
            FROM risk_decisions
            WHERE protected_action = :protectedAction
              AND actor_subject_id = :actorSubjectId
              AND resource_type IS NOT DISTINCT FROM :resourceType
              AND resource_id IS NOT DISTINCT FROM :resourceId
              AND command_id IS NOT DISTINCT FROM :commandId
              AND policy_epoch = :policyEpoch
            """)
    Optional<RiskDecision> findByEvaluationIdentity(@Param("protectedAction") String protectedAction,
                                                    @Param("actorSubjectId") UUID actorSubjectId,
                                                    @Param("resourceType") String resourceType,
                                                    @Param("resourceId") UUID resourceId,
                                                    @Param("commandId") UUID commandId,
                                                    @Param("policyEpoch") String policyEpoch);

    /**
     * Finds the decision recorded under one client idempotency key.
     *
     * <pre>{@code
     * SELECT * FROM risk_decisions WHERE client_idempotency_key = :clientIdempotencyKey
     * }</pre>
     *
     * <p>Matches {@code uk_risk_decisions_client_key}. Differing inputs under the same key are a
     * conflict the caller must be told about, not a second decision.</p>
     *
     * @param clientIdempotencyKey caller's key
     * @return the decision, when the key has been used
     */
    Optional<RiskDecision> findByClientIdempotencyKey(String clientIdempotencyKey);

    /**
     * Reads a subject's decision history.
     *
     * <pre>{@code
     * SELECT * FROM risk_decisions WHERE actor_subject_id = :actorSubjectId ORDER BY evaluated_at DESC
     * }</pre>
     *
     * @param actorSubjectId subject
     * @return possibly empty list, most recent first
     */
    List<RiskDecision> findByActorSubjectIdOrderByEvaluatedAtDesc(UUID actorSubjectId);

    /**
     * Claims decisions whose window has closed so their projection can be moved.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_decisions
     * WHERE projection = 'EFFECTIVE' AND expires_at IS NOT NULL AND expires_at <= :at
     * ORDER BY expires_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. Expiry moves the projection only; the outcome itself is frozen
     * by trigger, so a late worker cannot rewrite what was decided.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum decisions to claim
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM risk_decisions
            WHERE projection = 'EFFECTIVE' AND expires_at IS NOT NULL AND expires_at <= :at
            ORDER BY expires_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<RiskDecision> claimExpired(@Param("at") Instant at, @Param("batchSize") int batchSize);
}
