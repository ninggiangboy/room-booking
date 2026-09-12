package dev.ngb.backend.repository;

import dev.ngb.backend.model.BookingLineItem;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads the money lines of a booking.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code booking_line_items}.</p>
 */
public interface BookingLineItemRepository extends ListCrudRepository<BookingLineItem, UUID> {

    /**
     * Returns the lines of one revision of a booking.
     *
     * <p>Spring derives {@code WHERE booking_id = ? AND revision = ? ORDER BY line_number},
     * matching {@code uk_booking_line_items_number}.</p>
     *
     * @param bookingId booking whose lines are wanted
     * @param revision revision of the contract to read
     * @return possibly empty list of lines in presentation order
     */
    List<BookingLineItem> findAllByBookingIdAndRevisionOrderByLineNumber(
            UUID bookingId, int revision);

    /**
     * Returns the lines of a booking's newest revision.
     *
     * <pre>{@code
     * SELECT *
     * FROM booking_line_items
     * WHERE booking_id = :bookingId
     *   AND revision = (SELECT max(revision)
     *                   FROM booking_line_items
     *                   WHERE booking_id = :bookingId)
     * ORDER BY line_number
     * }</pre>
     *
     * <p>The revision is resolved in the database rather than by reading the booking first, so the
     * lines returned are self-consistent even if a modification commits between the two reads a
     * caller would otherwise have made.</p>
     *
     * @param bookingId booking whose current lines are wanted
     * @return possibly empty list of lines in presentation order
     */
    @Query("""
            SELECT *
            FROM booking_line_items
            WHERE booking_id = :bookingId
              AND revision = (SELECT max(revision)
                              FROM booking_line_items
                              WHERE booking_id = :bookingId)
            ORDER BY line_number
            """)
    List<BookingLineItem> findCurrent(@Param("bookingId") UUID bookingId);
}
