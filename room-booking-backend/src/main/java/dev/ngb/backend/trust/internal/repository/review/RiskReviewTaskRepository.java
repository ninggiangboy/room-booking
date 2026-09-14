package dev.ngb.backend.trust.internal.repository.review;

import dev.ngb.backend.trust.internal.model.review.RiskReviewTask;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.trust.internal.model.review.RiskReviewTask;


/**
 * Reads and claims human risk work.
 *
 * <p>Claiming takes a row lock and skips what other workers hold, and the claim itself is a lease
 * with a fencing token rather than an assignment: a worker that wakes up late carries an old token
 * and a trigger refuses its write.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_review_tasks}.</p>
 */
public interface RiskReviewTaskRepository extends ListCrudRepository<RiskReviewTask, UUID> {

    /**
     * Claims the most pressing unclaimed work in one queue.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_review_tasks
     * WHERE risk_review_queue_id = :riskReviewQueueId AND state = 'QUEUED'
     * ORDER BY deadline_at, priority_score DESC NULLS LAST
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. The deadline leads the ordering because a model score may order
     * comparable tasks but must never push an urgent one down the queue.</p>
     *
     * @param riskReviewQueueId queue to claim from
     * @param batchSize maximum tasks to claim
     * @return possibly empty list, most pressing first
     */
    @Query("""
            SELECT *
            FROM risk_review_tasks
            WHERE risk_review_queue_id = :riskReviewQueueId AND state = 'QUEUED'
            ORDER BY deadline_at, priority_score DESC NULLS LAST
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<RiskReviewTask> claimQueued(@Param("riskReviewQueueId") UUID riskReviewQueueId,
                                     @Param("batchSize") int batchSize);

    /**
     * Reclaims work whose lease has lapsed.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_review_tasks
     * WHERE state IN ('CLAIMED', 'IN_REVIEW') AND lease_expires_at <= :at
     * ORDER BY deadline_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. Reclaiming raises the fencing token, which is what makes the
     * previous holder's late write fail rather than overwrite a case somebody else has taken.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum tasks to reclaim
     * @return possibly empty list, most pressing first
     */
    @Query("""
            SELECT *
            FROM risk_review_tasks
            WHERE state IN ('CLAIMED', 'IN_REVIEW') AND lease_expires_at <= :at
            ORDER BY deadline_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<RiskReviewTask> claimAbandoned(@Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Reads the open work about one subject.
     *
     * <pre>{@code
     * SELECT * FROM risk_review_tasks
     * WHERE subject_id = :subjectId AND state NOT IN ('DECIDED', 'CANCELLED')
     * ORDER BY deadline_at
     * }</pre>
     *
     * @param subjectId subject
     * @return possibly empty list, most pressing first
     */
    @Query("""
            SELECT *
            FROM risk_review_tasks
            WHERE subject_id = :subjectId AND state NOT IN ('DECIDED', 'CANCELLED')
            ORDER BY deadline_at
            """)
    List<RiskReviewTask> findOpenForSubject(@Param("subjectId") UUID subjectId);

    /**
     * Lists work past its deadline, for alerting.
     *
     * <pre>{@code
     * SELECT * FROM risk_review_tasks
     * WHERE state NOT IN ('DECIDED', 'CANCELLED') AND deadline_at <= :at
     * ORDER BY deadline_at
     * }</pre>
     *
     * @param at instant to treat as now
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT *
            FROM risk_review_tasks
            WHERE state NOT IN ('DECIDED', 'CANCELLED') AND deadline_at <= :at
            ORDER BY deadline_at
            """)
    List<RiskReviewTask> findOverdue(@Param("at") Instant at);
}
