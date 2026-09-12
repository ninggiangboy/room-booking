package dev.ngb.backend.repository;

import dev.ngb.backend.model.BookingSupplySnapshot;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads what the guest was shown when they booked.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code booking_supply_snapshots}.</p>
 */
public interface BookingSupplySnapshotRepository
        extends ListCrudRepository<BookingSupplySnapshot, UUID> {

    /**
     * Returns the snapshot for a booking.
     *
     * <p>Spring derives {@code WHERE booking_id = ?}, matching
     * {@code uk_booking_supply_snapshots_booking}, which allows only one per booking.</p>
     *
     * @param bookingId booking whose presentation snapshot is wanted
     * @return the snapshot, when one was captured
     */
    Optional<BookingSupplySnapshot> findByBookingId(UUID bookingId);
}
