package dev.ngb.backend.review.internal.repository.publication;

import dev.ngb.backend.review.internal.model.publication.ReviewResponse;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.review.internal.model.publication.ReviewResponse;


/**
 * Reads host responses to reviews.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_responses}.</p>
 */
public interface ReviewResponseRepository extends ListCrudRepository<ReviewResponse, UUID> {

    /**
     * Finds the live response to a review.
     *
     * <pre>{@code
     * SELECT * FROM review_responses
     * WHERE review_record_id = :reviewRecordId AND state IN ('DRAFT', 'SUBMITTED')
     * }</pre>
     *
     * <p>Matches {@code uk_review_responses_live}, so at most one row can come back: one response per
     * review, so a rating cannot be buried under a thread.</p>
     *
     * @param reviewRecordId review
     * @return the live response, when there is one
     */
    @Query("""
            SELECT * FROM review_responses
            WHERE review_record_id = :reviewRecordId AND state IN ('DRAFT', 'SUBMITTED')
            """)
    Optional<ReviewResponse> findLive(@Param("reviewRecordId") UUID reviewRecordId);

    /**
     * Lists drafts whose response window is closing.
     *
     * <pre>{@code
     * SELECT *
     * FROM review_responses
     * WHERE state = 'DRAFT'
     *   AND response_deadline_at <= :at
     * ORDER BY response_deadline_at
     * }</pre>
     *
     * @param at instant to treat as now
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT *
            FROM review_responses
            WHERE state = 'DRAFT'
              AND response_deadline_at <= :at
            ORDER BY response_deadline_at
            """)
    List<ReviewResponse> findExpiredDrafts(@Param("at") Instant at);
}
