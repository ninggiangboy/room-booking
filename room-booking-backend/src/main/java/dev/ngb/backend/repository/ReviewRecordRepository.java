package dev.ngb.backend.repository;

import dev.ngb.backend.model.ReviewRecord;
import dev.ngb.backend.model.ReviewDirection;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads review aggregates.
 *
 * <p>No text comes back from here; the words are in revisions. What this answers is which review
 * exists, what state its five dimensions are in, and which revision currently stands.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_records}.</p>
 */
public interface ReviewRecordRepository extends ListCrudRepository<ReviewRecord, UUID> {

    /**
     * Finds the review a right produced.
     *
     * <p>Spring derives {@code WHERE review_right_id = ?}, matching {@code uk_review_records_right}.
     * Used to make submission idempotent.</p>
     *
     * @param reviewRightId right exercised
     * @return the review, when the right was exercised
     */
    Optional<ReviewRecord> findByReviewRightId(UUID reviewRightId);

    /**
     * Finds the review for one booking and direction.
     *
     * <p>Spring derives {@code WHERE booking_id = ? AND direction = ?}, matching
     * {@code uk_review_records_booking_direction}.</p>
     *
     * @param bookingId booking
     * @param direction who reviewed whom
     * @return the review, when it exists
     */
    Optional<ReviewRecord> findByBookingIdAndDirection(UUID bookingId, ReviewDirection direction);

    /**
     * Reads a page of a listing's public reviews.
     *
     * <pre>{@code
     * SELECT *
     * FROM review_records
     * WHERE listing_id = :listingId
     *   AND public_projection IN ('PUBLIC', 'PUBLIC_REDACTED')
     *   AND (:before IS NULL OR submitted_at < :before)
     * ORDER BY submitted_at DESC
     * LIMIT :limit
     * }</pre>
     *
     * <p>Matches {@code idx_review_records_listing}. The cursor is the submission instant rather than an
     * offset, so a review appearing or disappearing does not shift the page under the reader.</p>
     *
     * @param listingId listing
     * @param before cursor, or null for the first page
     * @param limit page size
     * @return possibly empty list, newest first
     */
    @Query("""
            SELECT *
            FROM review_records
            WHERE listing_id = :listingId
              AND public_projection IN ('PUBLIC', 'PUBLIC_REDACTED')
              AND (:before IS NULL OR submitted_at < :before)
            ORDER BY submitted_at DESC
            LIMIT :limit
            """)
    List<ReviewRecord> findPublicPage(@Param("listingId") UUID listingId,
            @Param("before") Instant before, @Param("limit") int limit);

    /**
     * Lists the reviews of one cycle.
     *
     * <p>Spring derives {@code WHERE review_cycle_id = ?}. Both directions come back, which is what the
     * reveal worker needs in order to decide whether both sides have spoken.</p>
     *
     * @param reviewCycleId cycle
     * @return possibly empty list
     */
    List<ReviewRecord> findByReviewCycleId(UUID reviewCycleId);

    /**
     * Locks one review for a state change.
     *
     * <pre>{@code
     * SELECT * FROM review_records WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction. Submission takes this lock, writes the revision and selects it,
     * all in one go.</p>
     *
     * @param id review to lock
     * @return the locked review, when it exists
     */
    @Query("SELECT * FROM review_records WHERE id = :id FOR UPDATE")
    Optional<ReviewRecord> findByIdForUpdate(@Param("id") UUID id);
}
