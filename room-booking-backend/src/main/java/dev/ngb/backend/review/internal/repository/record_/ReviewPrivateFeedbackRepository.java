package dev.ngb.backend.review.internal.repository.record_;

import dev.ngb.backend.review.internal.model.record_.ReviewPrivateFeedback;
import dev.ngb.backend.review.internal.model.record_.PrivateFeedbackAudience;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.UUID;

/**
 * Reads private feedback.
 *
 * <p>Excluded from aggregates and ordinary aspect processing by stored flags. Any consumer here is
 * reading material the author expected to stay private, so reads are purpose-scoped by the service
 * above this interface.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_private_feedback}.</p>
 */
public interface ReviewPrivateFeedbackRepository extends ListCrudRepository<ReviewPrivateFeedback, UUID> {

    /**
     * Lists the private feedback written under one right.
     *
     * <p>Spring derives {@code WHERE review_right_id = ?}.</p>
     *
     * @param reviewRightId right
     * @return possibly empty list
     */
    List<ReviewPrivateFeedback> findByReviewRightId(UUID reviewRightId);

    /**
     * Lists the private feedback addressed to one audience under a right.
     *
     * <p>Spring derives {@code WHERE review_right_id = ? AND audience = ?}.</p>
     *
     * @param reviewRightId right
     * @param audience who it was written for
     * @return possibly empty list
     */
    List<ReviewPrivateFeedback> findByReviewRightIdAndAudience(UUID reviewRightId,
            PrivateFeedbackAudience audience);
}
