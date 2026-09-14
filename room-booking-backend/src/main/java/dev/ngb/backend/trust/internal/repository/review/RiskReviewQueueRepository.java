package dev.ngb.backend.trust.internal.repository.review;

import dev.ngb.backend.trust.internal.model.review.RiskReviewQueue;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the queues human risk work waits in.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code risk_review_queues}.</p>
 */
public interface RiskReviewQueueRepository extends ListCrudRepository<RiskReviewQueue, UUID> {

    /**
     * Finds one queue.
     *
     * <pre>{@code
     * SELECT * FROM risk_review_queues WHERE queue_key = :queueKey
     * }</pre>
     *
     * @param queueKey stable queue key
     * @return the queue, when it exists
     */
    Optional<RiskReviewQueue> findByQueueKey(String queueKey);

    /**
     * Lists queues a reviewer with one skill may take work from.
     *
     * <pre>{@code
     * SELECT * FROM risk_review_queues WHERE required_skill = :requiredSkill AND status = 'ACTIVE'
     * }</pre>
     *
     * @param requiredSkill the reviewer's skill
     * @return possibly empty list
     */
    @Query("SELECT * FROM risk_review_queues WHERE required_skill = :requiredSkill AND status = 'ACTIVE'")
    List<RiskReviewQueue> findActiveBySkill(@Param("requiredSkill") String requiredSkill);
}
