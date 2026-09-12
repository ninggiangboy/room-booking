package dev.ngb.backend.repository;

import dev.ngb.backend.model.BookingTimelineEntry;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads the human-readable account of a booking.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code booking_timeline_entries}.</p>
 */
public interface BookingTimelineEntryRepository
        extends ListCrudRepository<BookingTimelineEntry, UUID> {

    /**
     * Returns every entry on a booking, including internal ones.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY sequence_number DESC}.</p>
     *
     * <p>For staff use only. Rendering the result to a guest or host would disclose internal notes;
     * use {@link #findVisibleTo} for anything either party will see.</p>
     *
     * @param bookingId booking whose timeline is wanted
     * @return possibly empty list of entries, newest first
     */
    List<BookingTimelineEntry> findAllByBookingIdOrderBySequenceNumberDesc(UUID bookingId);

    /**
     * Returns the entries one audience is allowed to see.
     *
     * <pre>{@code
     * SELECT *
     * FROM booking_timeline_entries
     * WHERE booking_id = :bookingId
     *   AND visibility IN (:audience, 'BOTH')
     * ORDER BY sequence_number DESC
     * }</pre>
     *
     * <p>The filter is applied in the query rather than after loading, so an internal note cannot
     * reach a caller that then forgets to remove it. {@code INTERNAL} matches no audience value and
     * is therefore unreachable through this method by construction.</p>
     *
     * @param bookingId booking whose timeline is wanted
     * @param audience {@code GUEST} or {@code HOST}
     * @return possibly empty list of entries that audience may see, newest first
     */
    @Query("""
            SELECT *
            FROM booking_timeline_entries
            WHERE booking_id = :bookingId
              AND visibility IN (:audience, 'BOTH')
            ORDER BY sequence_number DESC
            """)
    List<BookingTimelineEntry> findVisibleTo(
            @Param("bookingId") UUID bookingId,
            @Param("audience") String audience);
}
