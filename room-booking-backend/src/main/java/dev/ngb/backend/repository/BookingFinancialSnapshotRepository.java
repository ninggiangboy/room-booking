package dev.ngb.backend.repository;

import dev.ngb.backend.model.BookingFinancialSnapshot;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what a booking was worth at each revision.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select and insert SQL
 * for {@code booking_financial_snapshots}. The inherited update and delete methods will fail at
 * runtime: the table is append-only and a database trigger refuses both. Correct a wrong figure by
 * saving the next revision.</p>
 */
public interface BookingFinancialSnapshotRepository
        extends ListCrudRepository<BookingFinancialSnapshot, UUID> {

    /**
     * Returns a booking's snapshots, newest revision first.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY revision DESC}.</p>
     *
     * @param bookingId booking whose financial history is wanted
     * @return possibly empty list of snapshots
     */
    List<BookingFinancialSnapshot> findAllByBookingIdOrderByRevisionDesc(UUID bookingId);

    /**
     * Returns one specific revision.
     *
     * <p>Spring derives {@code WHERE booking_id = ? AND revision = ?}, matching
     * {@code uk_booking_financial_snapshots_revision}.</p>
     *
     * @param bookingId booking to read
     * @param revision revision wanted
     * @return the snapshot at that revision, when it exists
     */
    Optional<BookingFinancialSnapshot> findByBookingIdAndRevision(UUID bookingId, int revision);

    /**
     * Returns a booking's newest snapshot.
     *
     * <pre>{@code
     * SELECT *
     * FROM booking_financial_snapshots
     * WHERE booking_id = :bookingId
     * ORDER BY revision DESC
     * LIMIT 1
     * }</pre>
     *
     * @param bookingId booking whose current figures are wanted
     * @return the latest snapshot, when the booking has one
     */
    @Query("""
            SELECT *
            FROM booking_financial_snapshots
            WHERE booking_id = :bookingId
            ORDER BY revision DESC
            LIMIT 1
            """)
    Optional<BookingFinancialSnapshot> findLatest(@Param("bookingId") UUID bookingId);
}
