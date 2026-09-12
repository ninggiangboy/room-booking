package dev.ngb.backend.repository;

import dev.ngb.backend.model.ReviewMedia;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads files attached to reviews.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_media}.</p>
 */
public interface ReviewMediaRepository extends ListCrudRepository<ReviewMedia, UUID> {

    /**
     * Lists the files attached to a revision, in display order.
     *
     * <p>Spring derives {@code WHERE review_revision_id = ? ORDER BY display_order}.</p>
     *
     * @param reviewRevisionId revision
     * @return possibly empty list
     */
    List<ReviewMedia> findByReviewRevisionIdOrderByDisplayOrder(UUID reviewRevisionId);

    /**
     * Claims uploads waiting to be scanned.
     *
     * <pre>{@code
     * SELECT *
     * FROM review_media
     * WHERE scan_state IN ('PENDING', 'FAILED')
     * ORDER BY created_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. Nothing unscanned is ever shown, so this queue is what stands
     * between an upload and the public.</p>
     *
     * @param batchSize maximum files to claim
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM review_media
            WHERE scan_state IN ('PENDING', 'FAILED')
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<ReviewMedia> claimPendingScans(@Param("batchSize") int batchSize);
}
