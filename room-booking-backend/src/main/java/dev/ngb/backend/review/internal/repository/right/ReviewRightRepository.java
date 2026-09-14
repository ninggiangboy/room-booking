package dev.ngb.backend.review.internal.repository.right;

import dev.ngb.backend.review.internal.model.right.ReviewRight;
import dev.ngb.backend.review.internal.model.ReviewDirection;
import dev.ngb.backend.review.internal.model.right.ReviewRightState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.review.internal.model.ReviewDirection;
import dev.ngb.backend.review.internal.model.right.ReviewRight;
import dev.ngb.backend.review.internal.model.right.ReviewRightState;


/**
 * Reads the bounded permissions to review.
 *
 * <p>Authorization for a submission starts here: the right names who may write, about what, and
 * until when.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_rights}.</p>
 */
public interface ReviewRightRepository extends ListCrudRepository<ReviewRight, UUID> {

    /**
     * Finds the right for one direction of a cycle.
     *
     * <p>Spring derives {@code WHERE review_cycle_id = ? AND direction = ?}, matching
     * {@code uk_review_rights_cycle_direction}.</p>
     *
     * @param reviewCycleId cycle
     * @param direction who would be reviewing whom
     * @return the right, when it exists
     */
    Optional<ReviewRight> findByReviewCycleIdAndDirection(UUID reviewCycleId, ReviewDirection direction);

    /**
     * Lists what one person may still write, soonest deadline first.
     *
     * <p>Spring derives {@code WHERE author_account_holder_id = ? AND state = ? ORDER BY deadline_at},
     * matching {@code idx_review_rights_author} when the state is {@code OPEN}.</p>
     *
     * @param authorAccountHolderId prospective author
     * @param state state to match, normally {@code OPEN}
     * @return possibly empty list, most urgent first
     */
    List<ReviewRight> findByAuthorAccountHolderIdAndStateOrderByDeadlineAt(UUID authorAccountHolderId,
            ReviewRightState state);

    /**
     * Lists rights whose window has closed without being used.
     *
     * <pre>{@code
     * SELECT *
     * FROM review_rights
     * WHERE state = 'OPEN'
     *   AND deadline_at <= :at
     * ORDER BY deadline_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum rights to claim
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM review_rights
            WHERE state = 'OPEN'
              AND deadline_at <= :at
            ORDER BY deadline_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<ReviewRight> claimExpired(@Param("at") Instant at, @Param("batchSize") int batchSize);
}
