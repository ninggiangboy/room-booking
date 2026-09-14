package dev.ngb.backend.trust.internal.repository.review;

import dev.ngb.backend.trust.internal.model.review.RiskReviewAction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.trust.internal.model.review.RiskReviewAction;


/**
 * Reads what reviewers did.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_review_actions}.</p>
 */
public interface RiskReviewActionRepository extends ListCrudRepository<RiskReviewAction, UUID> {

    /**
     * Reads one task's reviewer actions.
     *
     * <pre>{@code
     * SELECT * FROM risk_review_actions WHERE risk_review_task_id = :riskReviewTaskId
     * ORDER BY action_sequence
     * }</pre>
     *
     * @param riskReviewTaskId the task
     * @return possibly empty list, in order
     */
    List<RiskReviewAction> findByRiskReviewTaskIdOrderByActionSequence(UUID riskReviewTaskId);

    /**
     * Reads a reviewer's own actions over a window, for quality sampling.
     *
     * <pre>{@code
     * SELECT *
     * FROM risk_review_actions
     * WHERE reviewer_account_holder_id = :reviewerAccountHolderId
     *   AND acted_at >= :from
     *   AND acted_at < :to
     * ORDER BY acted_at
     * }</pre>
     *
     * @param reviewerAccountHolderId the reviewer
     * @param from inclusive start
     * @param to exclusive end
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM risk_review_actions
            WHERE reviewer_account_holder_id = :reviewerAccountHolderId
              AND acted_at >= :from
              AND acted_at < :to
            ORDER BY acted_at
            """)
    List<RiskReviewAction> findByReviewerAndWindow(
            @Param("reviewerAccountHolderId") UUID reviewerAccountHolderId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
