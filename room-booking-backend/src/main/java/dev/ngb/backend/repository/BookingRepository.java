package dev.ngb.backend.repository;

import dev.ngb.backend.model.Booking;
import dev.ngb.backend.model.BookingLifecycleState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and writes booking contracts.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code bookings}.</p>
 */
public interface BookingRepository extends ListCrudRepository<Booking, UUID> {

    /**
     * Finds a booking by the confirmation code guest and host actually use.
     *
     * <p>Spring derives {@code WHERE public_id = ?}, matching {@code uk_bookings_public_id}.</p>
     *
     * @param publicId confirmation code
     * @return the booking, when one bears that code
     */
    Optional<Booking> findByPublicId(String publicId);

    /**
     * Finds the booking an offer produced.
     *
     * <p>Spring derives {@code WHERE quote_id = ?}, matching {@code uk_bookings_quote}. Because that
     * constraint is unique, a retried acceptance can use this to recognise that the work is already
     * done instead of attempting a second contract from one intention.</p>
     *
     * @param quoteId quote that was accepted
     * @return the booking formed from it, when one exists
     */
    Optional<Booking> findByQuoteId(UUID quoteId);

    /**
     * Locks a booking for a state transition.
     *
     * <pre>{@code
     * SELECT *
     * FROM bookings
     * WHERE id = :id
     * FOR UPDATE
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> Confirmation, cancellation, and
     * modification each read the booking, decide, and write; without the lock two such commands
     * interleave and the second overwrites a decision it never saw. The optimistic {@code version}
     * alone is not enough here, because the compensating work these commands do — releasing claims,
     * instructing refunds — cannot be undone by a retry.</p>
     *
     * @param id booking to lock
     * @return the locked booking, when it exists
     */
    @Query("""
            SELECT *
            FROM bookings
            WHERE id = :id
            FOR UPDATE
            """)
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Returns provisional bookings whose hold deadline has passed, for the release sweep.
     *
     * <pre>{@code
     * SELECT *
     * FROM bookings
     * WHERE lifecycle_state = 'PROVISIONAL'
     *   AND hold_expires_at <= :decisionInstant
     * ORDER BY hold_expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>The instant is bound by the caller rather than read by the database. Time passing does not
     * change an index entry, so nothing expires on its own: a worker has to transition these rows
     * explicitly, and until it does the nights stay consumed, which is the safe direction to
     * fail.</p>
     *
     * @param decisionInstant the sweep's single decision instant
     * @param batchSize maximum number of bookings to return
     * @return possibly empty list, most overdue first
     */
    @Query("""
            SELECT *
            FROM bookings
            WHERE lifecycle_state = 'PROVISIONAL'
              AND hold_expires_at <= :decisionInstant
            ORDER BY hold_expires_at
            LIMIT :batchSize
            """)
    List<Booking> findLapsedProvisional(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);

    /**
     * Returns a guest's bookings, most recent stay first.
     *
     * <p>Spring derives {@code WHERE guest_account_holder_id = ? ORDER BY check_in_date DESC}.</p>
     *
     * @param guestAccountHolderId guest whose bookings are wanted
     * @return possibly empty list of bookings
     */
    List<Booking> findAllByGuestAccountHolderIdOrderByCheckInDateDesc(UUID guestAccountHolderId);

    /**
     * Returns a host's bookings in one lifecycle state.
     *
     * <p>Spring derives {@code WHERE host_account_holder_id = ? AND lifecycle_state = ? ORDER BY
     * check_in_date}. Used for the host's arrivals and departures views.</p>
     *
     * @param hostAccountHolderId host whose bookings are wanted
     * @param lifecycleState state to filter on
     * @return possibly empty list of bookings, soonest arrival first
     */
    List<Booking> findAllByHostAccountHolderIdAndLifecycleStateOrderByCheckInDate(
            UUID hostAccountHolderId, BookingLifecycleState lifecycleState);

    /**
     * Returns confirmed bookings arriving on a date.
     *
     * <pre>{@code
     * SELECT *
     * FROM bookings
     * WHERE accommodation_type_id = :accommodationTypeId
     *   AND check_in_date = :arrivalDate
     *   AND lifecycle_state = 'CONFIRMED'
     * ORDER BY check_in_instant
     * }</pre>
     *
     * <p>The date is civil and belongs to the property's own time zone, which is why it is compared
     * as a date and not derived from an instant.</p>
     *
     * @param accommodationTypeId supply being prepared
     * @param arrivalDate the local arrival date
     * @return possibly empty list of arriving bookings
     */
    @Query("""
            SELECT *
            FROM bookings
            WHERE accommodation_type_id = :accommodationTypeId
              AND check_in_date = :arrivalDate
              AND lifecycle_state = 'CONFIRMED'
            ORDER BY check_in_instant
            """)
    List<Booking> findConfirmedArrivals(
            @Param("accommodationTypeId") UUID accommodationTypeId,
            @Param("arrivalDate") LocalDate arrivalDate);

    /**
     * Returns confirmed bookings whose contractual checkout has passed, for the completion sweep.
     *
     * <pre>{@code
     * SELECT *
     * FROM bookings
     * WHERE lifecycle_state = 'CONFIRMED'
     *   AND stay_state <> 'NO_SHOW'
     *   AND check_out_instant <= :decisionInstant
     * ORDER BY check_out_instant
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Compared on the resolved instant rather than the civil date, because a checkout hour in one
     * time zone is not a checkout hour in another and completion decides real entitlements. The
     * caller is expected to have added its own grace period to the instant it binds.</p>
     *
     * @param decisionInstant the sweep's decision instant, grace already applied
     * @param batchSize maximum number of bookings to return
     * @return possibly empty list, longest finished first
     */
    @Query("""
            SELECT *
            FROM bookings
            WHERE lifecycle_state = 'CONFIRMED'
              AND stay_state <> 'NO_SHOW'
              AND check_out_instant <= :decisionInstant
            ORDER BY check_out_instant
            LIMIT :batchSize
            """)
    List<Booking> findCompletable(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);
}
