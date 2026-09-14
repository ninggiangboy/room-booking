package dev.ngb.backend.trust.internal.repository.intervention;

import dev.ngb.backend.trust.internal.model.intervention.RiskChallenge;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.trust.internal.model.intervention.RiskChallenge;


/**
 * Reads step-up challenges.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_challenges}.</p>
 */
public interface RiskChallengeRepository extends ListCrudRepository<RiskChallenge, UUID> {

    /**
     * Finds the live challenge for one decision and method.
     *
     * <pre>{@code
     * SELECT * FROM risk_challenges
     * WHERE risk_decision_id = :riskDecisionId
     *   AND challenge_method = :challengeMethod
     *   AND state IN ('REQUIRED', 'STARTED', 'SUBMITTED')
     * }</pre>
     *
     * <p>Matches {@code uk_risk_challenges_live}, so at most one row can come back. Issuing a second one
     * is how an attempt ceiling gets reset by asking again, which the index prevents.</p>
     *
     * @param riskDecisionId the decision that demanded proof
     * @param challengeMethod kind of proof
     * @return the live challenge, when there is one
     */
    @Query("""
            SELECT *
            FROM risk_challenges
            WHERE risk_decision_id = :riskDecisionId
              AND challenge_method = :challengeMethod
              AND state IN ('REQUIRED', 'STARTED', 'SUBMITTED')
            """)
    Optional<RiskChallenge> findLive(@Param("riskDecisionId") UUID riskDecisionId,
                                     @Param("challengeMethod") String challengeMethod);

    /**
     * Reads a subject's challenges.
     *
     * <pre>{@code
     * SELECT * FROM risk_challenges WHERE subject_id = :subjectId ORDER BY issued_at DESC
     * }</pre>
     *
     * @param subjectId subject
     * @return possibly empty list, most recent first
     */
    List<RiskChallenge> findBySubjectIdOrderByIssuedAtDesc(UUID subjectId);

    /**
     * Claims challenges whose window has closed.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_challenges
     * WHERE state_rank < 3 AND expires_at <= :at
     * ORDER BY expires_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. The expiry itself is snapshotted on the row, so a worker
     * running late still closes the challenge at the deadline it was issued with.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum challenges to claim
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM risk_challenges
            WHERE state_rank < 3 AND expires_at <= :at
            ORDER BY expires_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<RiskChallenge> claimExpired(@Param("at") Instant at, @Param("batchSize") int batchSize);
}
