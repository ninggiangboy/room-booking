package dev.ngb.backend.repository;

import dev.ngb.backend.model.BookingItem;
import dev.ngb.backend.model.BookingItemStatus;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the claimed resources that make up a booking.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code booking_items}.</p>
 */
public interface BookingItemRepository extends ListCrudRepository<BookingItem, UUID> {

    /**
     * Returns a booking's items in order.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY item_number}.</p>
     *
     * @param bookingId booking whose items are wanted
     * @return possibly empty list of items
     */
    List<BookingItem> findAllByBookingIdOrderByItemNumber(UUID bookingId);

    /**
     * Returns a booking's items in one status.
     *
     * <p>Spring derives {@code WHERE booking_id = ? AND item_status = ?}. Used when cancelling part
     * of a stay, to act on the items that still hold nights and leave the rest alone.</p>
     *
     * @param bookingId booking whose items are wanted
     * @param itemStatus status to filter on
     * @return possibly empty list of items
     */
    List<BookingItem> findAllByBookingIdAndItemStatus(UUID bookingId, BookingItemStatus itemStatus);
}
