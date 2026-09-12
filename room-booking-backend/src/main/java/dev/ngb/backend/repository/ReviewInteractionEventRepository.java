package dev.ngb.backend.repository;

import dev.ngb.backend.model.ReviewInteractionEvent;
import dev.ngb.backend.model.ReviewInteractionType;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads public interaction with reviews.
 *
 * <p>Append-only and deliberately narrow. This is the table most likely to move to analytical
 * storage, and nothing in the review write path depends on reading it.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_interaction_events}.</p>
 */
public interface ReviewInteractionEventRepository extends ListCrudRepository<ReviewInteractionEvent, UUID> {

    /**
     * Finds whether a request has already been recorded.
     *
     * <p>Spring derives {@code WHERE request_id = ? AND interaction_type = ?}, matching
     * {@code uk_review_interaction_events_request}.</p>
     *
     * @param requestId request identity
     * @param interactionType what was done
     * @return the event, when it was already recorded
     */
    Optional<ReviewInteractionEvent> findByRequestIdAndInteractionType(String requestId,
            ReviewInteractionType interactionType);

    /**
     * Lists recent interactions with one review.
     *
     * <p>Spring derives {@code WHERE review_record_id = ? ORDER BY occurred_at DESC}.</p>
     *
     * @param reviewRecordId review
     * @return possibly empty list, most recent first
     */
    List<ReviewInteractionEvent> findByReviewRecordIdOrderByOccurredAtDesc(UUID reviewRecordId);
}
