package dev.ngb.backend.repository;

import dev.ngb.backend.model.BookingNight;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reads what was agreed for each night of a booking.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code booking_nights}.</p>
 */
public interface BookingNightRepository extends ListCrudRepository<BookingNight, UUID> {

    /**
     * Returns a booking's nights in date order.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY stay_date}. Includes released nights:
     * a shortened stay keeps the nights it once sold, and a caller that wants only the live ones
     * should ask for those explicitly.</p>
     *
     * @param bookingId booking whose nights are wanted
     * @return possibly empty list of nights, earliest first
     */
    List<BookingNight> findAllByBookingIdOrderByStayDate(UUID bookingId);

    /**
     * Returns the nights of a booking that still consume inventory.
     *
     * <pre>{@code
     * SELECT *
     * FROM booking_nights
     * WHERE booking_id = :bookingId
     *   AND is_released = false
     * ORDER BY stay_date
     * }</pre>
     *
     * @param bookingId booking whose live nights are wanted
     * @return possibly empty list of unreleased nights
     */
    @Query("""
            SELECT *
            FROM booking_nights
            WHERE booking_id = :bookingId
              AND is_released = false
            ORDER BY stay_date
            """)
    List<BookingNight> findSold(@Param("bookingId") UUID bookingId);

    /**
     * Returns the nights a booking still holds from a date onward.
     *
     * <pre>{@code
     * SELECT *
     * FROM booking_nights
     * WHERE booking_id = :bookingId
     *   AND is_released = false
     *   AND stay_date >= :fromDate
     * ORDER BY stay_date
     * }</pre>
     *
     * <p>The shape early departure needs. Cancelling mid-stay releases only nights the guest has not
     * yet occupied; nights already stayed are not the platform's to give back.</p>
     *
     * @param bookingId booking being shortened
     * @param fromDate first date that may be released, inclusive
     * @return possibly empty list of releasable nights
     */
    @Query("""
            SELECT *
            FROM booking_nights
            WHERE booking_id = :bookingId
              AND is_released = false
              AND stay_date >= :fromDate
            ORDER BY stay_date
            """)
    List<BookingNight> findReleasableFrom(
            @Param("bookingId") UUID bookingId,
            @Param("fromDate") LocalDate fromDate);
}
