package dev.ngb.backend.repository;

import dev.ngb.backend.model.CancellationPreview;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads quoted cancellation figures.
 *
 * <p>A preview is redeemable only while it is active and unexpired, so the redemption read binds the
 * instant rather than calling the clock: the freshness decision belongs to the caller that is about to
 * commit a decision against it.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code cancellation_previews}.</p>
 */
public interface CancellationPreviewRepository extends ListCrudRepository<CancellationPreview, UUID> {

    /**
     * Finds a preview that may still found a decision.
     *
     * <pre>{@code
     * SELECT *
     * FROM cancellation_previews
     * WHERE id = :id AND status = 'ACTIVE' AND expires_at > :at
     * }</pre>
     *
     * <p>Returning empty is the ordinary answer when a guest sat on a quote too long, and the caller must
     * offer a fresh preview rather than settle the stale figure.</p>
     *
     * @param id preview being redeemed
     * @param at instant the redemption is happening at
     * @return the preview, when it is still redeemable
     */
    @Query("""
            SELECT *
            FROM cancellation_previews
            WHERE id = :id AND status = 'ACTIVE' AND expires_at > :at
            """)
    Optional<CancellationPreview> findRedeemable(@Param("id") UUID id, @Param("at") Instant at);

    /**
     * Returns a booking's previews, most recent first.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY created_at DESC}.</p>
     *
     * @param bookingId booking whose previews are wanted
     * @return possibly empty list, newest first
     */
    List<CancellationPreview> findAllByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /**
     * Returns active previews whose window has closed.
     *
     * <pre>{@code
     * SELECT *
     * FROM cancellation_previews
     * WHERE status = 'ACTIVE' AND expires_at <= :at
     * ORDER BY expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>The sweeper's read. {@code idx_cancellation_previews_active} covers it, and the index predicate
     * deliberately names no time function -- one that did would stop matching the rows it was built for as
     * the clock moved.</p>
     *
     * @param at instant to judge expiry against
     * @param batchSize maximum rows to return
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM cancellation_previews
            WHERE status = 'ACTIVE' AND expires_at <= :at
            ORDER BY expires_at
            LIMIT :batchSize
            """)
    List<CancellationPreview> findExpired(@Param("at") Instant at, @Param("batchSize") int batchSize);
}
