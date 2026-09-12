package dev.ngb.backend.repository;

import dev.ngb.backend.model.BookingPartyRole;
import dev.ngb.backend.model.BookingPartySnapshot;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads who the parties to a booking were.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code booking_party_snapshots}.</p>
 */
public interface BookingPartySnapshotRepository
        extends ListCrudRepository<BookingPartySnapshot, UUID> {

    /**
     * Returns every party on a booking.
     *
     * <p>Spring derives {@code WHERE booking_id = ?}.</p>
     *
     * @param bookingId booking whose parties are wanted
     * @return possibly empty list of party snapshots
     */
    List<BookingPartySnapshot> findAllByBookingId(UUID bookingId);

    /**
     * Returns the party filling one role on a booking.
     *
     * <p>Spring derives {@code WHERE booking_id = ? AND party_role = ?}, matching
     * {@code uk_booking_party_snapshots_role}, which allows at most one party per role.</p>
     *
     * @param bookingId booking to read
     * @param partyRole capacity wanted
     * @return the party in that capacity, when one was recorded
     */
    Optional<BookingPartySnapshot> findByBookingIdAndPartyRole(
            UUID bookingId, BookingPartyRole partyRole);
}
