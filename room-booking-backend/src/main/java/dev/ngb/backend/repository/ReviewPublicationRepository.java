package dev.ngb.backend.repository;

import dev.ngb.backend.model.ReviewPublication;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads when reviews were visible.
 *
 * <p>Intervals, not timestamps. The open-interval lookup answers "is it visible now"; the full list
 * answers "was it visible then", which is the question a dispute actually asks.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_publications}.</p>
 */
public interface ReviewPublicationRepository extends ListCrudRepository<ReviewPublication, UUID> {

    /**
     * Finds the interval a review is currently visible under.
     *
     * <pre>{@code
     * SELECT * FROM review_publications
     * WHERE review_record_id = :reviewRecordId AND published_until IS NULL
     * }</pre>
     *
     * <p>Matches {@code uk_review_publications_open}, so at most one row can come back.</p>
     *
     * @param reviewRecordId review
     * @return the open interval, when the review is visible
     */
    @Query("""
            SELECT * FROM review_publications
            WHERE review_record_id = :reviewRecordId AND published_until IS NULL
            """)
    Optional<ReviewPublication> findOpenInterval(@Param("reviewRecordId") UUID reviewRecordId);

    /**
     * Lists every interval a review was visible for, newest first.
     *
     * <p>Spring derives {@code WHERE review_record_id = ? ORDER BY published_from DESC}. Removal and
     * restoration leave two rows rather than rewriting one.</p>
     *
     * @param reviewRecordId review
     * @return possibly empty list, newest interval first
     */
    List<ReviewPublication> findByReviewRecordIdOrderByPublishedFromDesc(UUID reviewRecordId);
}
