package dev.ngb.backend.repository;

import dev.ngb.backend.model.BookingRevisionNight;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

/**
 * Reads the nightly shape of a booking revision.
 *
 * <p>Nights are per revision, so shortening a stay leaves the original nights readable rather than
 * deleting them.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code booking_revision_nights}.</p>
 */
public interface BookingRevisionNightRepository extends ListCrudRepository<BookingRevisionNight, UUID> {

    /**
     * Returns one revision's nights in stay order.
     *
     * <p>Spring derives {@code WHERE booking_revision_id = ? ORDER BY stay_date}.</p>
     *
     * @param bookingRevisionId revision whose nights are wanted
     * @return possibly empty list, earliest night first
     */
    List<BookingRevisionNight> findAllByBookingRevisionIdOrderByStayDate(UUID bookingRevisionId);

    /**
     * Returns the nights held under one inventory claim.
     *
     * <p>Spring derives {@code WHERE inventory_claim_id = ? ORDER BY stay_date}. This is the read that
     * answers "what is this claim actually holding" before a release instruction is written.</p>
     *
     * @param inventoryClaimId claim to inspect
     * @return possibly empty list, earliest night first
     */
    List<BookingRevisionNight> findAllByInventoryClaimIdOrderByStayDate(UUID inventoryClaimId);
}
