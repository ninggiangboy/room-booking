package dev.ngb.backend.repository;

import dev.ngb.backend.model.BookingRevision;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the terms a booking has held over its life.
 *
 * <p>Every settlement question is really a question about a revision: which terms were in force, what
 * the stay was, what it was worth. The current-revision read is a query rather than a derived name
 * because "current" is a partial-index predicate, not a column.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code booking_revisions}.</p>
 */
public interface BookingRevisionRepository extends ListCrudRepository<BookingRevision, UUID> {

    /**
     * Returns the booking's current terms.
     *
     * <pre>{@code
     * SELECT *
     * FROM booking_revisions
     * WHERE booking_id = :bookingId AND status = 'COMMITTED'
     * }</pre>
     *
     * <p>At most one row can match: {@code uk_booking_revisions_current} is a unique index over
     * {@code booking_id} where the status is committed.</p>
     *
     * @param bookingId booking whose terms are wanted
     * @return the current revision, when the booking has one
     */
    @Query("""
            SELECT *
            FROM booking_revisions
            WHERE booking_id = :bookingId AND status = 'COMMITTED'
            """)
    Optional<BookingRevision> findCurrent(@Param("bookingId") UUID bookingId);

    /**
     * Locks the booking's current terms for update.
     *
     * <pre>{@code
     * SELECT *
     * FROM booking_revisions
     * WHERE booking_id = :bookingId AND status = 'COMMITTED'
     * FOR UPDATE
     * }</pre>
     *
     * <p>Must be called inside a transaction. This is the lock a modification takes before superseding the
     * current revision, so that two concurrent modifications cannot both believe they describe the next
     * one.</p>
     *
     * @param bookingId booking whose terms are being replaced
     * @return the locked current revision, when the booking has one
     */
    @Query("""
            SELECT *
            FROM booking_revisions
            WHERE booking_id = :bookingId AND status = 'COMMITTED'
            FOR UPDATE
            """)
    Optional<BookingRevision> findCurrentForUpdate(@Param("bookingId") UUID bookingId);

    /**
     * Finds one revision of a booking by its number.
     *
     * <p>Spring derives {@code WHERE booking_id = ? AND revision_number = ?}, matching
     * {@code uk_booking_revisions_number}.</p>
     *
     * @param bookingId booking the revision belongs to
     * @param revisionNumber number within the booking
     * @return the revision, when one exists
     */
    Optional<BookingRevision> findByBookingIdAndRevisionNumber(UUID bookingId, int revisionNumber);

    /**
     * Returns a booking's revision chain, newest first.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY revision_number DESC}.</p>
     *
     * @param bookingId booking whose history is wanted
     * @return possibly empty list, newest revision first
     */
    List<BookingRevision> findAllByBookingIdOrderByRevisionNumberDesc(UUID bookingId);
}
