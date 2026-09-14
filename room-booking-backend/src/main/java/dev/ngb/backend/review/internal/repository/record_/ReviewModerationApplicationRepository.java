package dev.ngb.backend.review.internal.repository.record_;

import dev.ngb.backend.review.internal.model.record_.ReviewModerationApplication;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.review.internal.model.record_.ReviewModerationApplication;


/**
 * Reads applied moderation decisions.
 *
 * <p>Append-only by trigger. The source-event lookup is the deduplication check a consumer runs
 * before applying anything.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_moderation_applications}.</p>
 */
public interface ReviewModerationApplicationRepository extends ListCrudRepository<ReviewModerationApplication, UUID> {

    /**
     * Finds whether a decision event has already been applied.
     *
     * <p>Spring derives {@code WHERE source_event_id = ?}, matching
     * {@code uk_review_moderation_applications_event}. A replayed event changes nothing twice.</p>
     *
     * @param sourceEventId event carrying the decision
     * @return the application, when it was already processed
     */
    Optional<ReviewModerationApplication> findBySourceEventId(UUID sourceEventId);

    /**
     * Lists what moderation has said about one exact revision, newest first.
     *
     * <p>Spring derives {@code WHERE review_revision_id = ? ORDER BY applied_at DESC}.</p>
     *
     * @param reviewRevisionId revision
     * @return possibly empty list
     */
    List<ReviewModerationApplication> findByReviewRevisionIdOrderByAppliedAtDesc(UUID reviewRevisionId);
}
