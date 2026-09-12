package dev.ngb.backend.repository;

import dev.ngb.backend.model.BookingCheckout;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads attempts to turn an offer into a booking.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code booking_checkouts}.</p>
 */
public interface BookingCheckoutRepository extends ListCrudRepository<BookingCheckout, UUID> {

    /**
     * Finds the attempt a given idempotency key already started.
     *
     * <p>Spring derives {@code WHERE idempotency_key = ?}, matching
     * {@code uk_booking_checkouts_idempotency}. This is the read that turns a duplicate submission
     * into a replay of the original outcome instead of a second booking.</p>
     *
     * @param idempotencyKey key the caller supplied
     * @return the attempt already recorded under that key, when one exists
     */
    Optional<BookingCheckout> findByIdempotencyKey(String idempotencyKey);

    /**
     * Finds an attempt by the reference shown to the guest.
     *
     * <p>Spring derives {@code WHERE public_id = ?}.</p>
     *
     * @param publicId reference shown to the guest
     * @return the attempt, when one bears that reference
     */
    Optional<BookingCheckout> findByPublicId(String publicId);

    /**
     * Returns unfinished attempts whose deadline has passed, for the expiry sweep.
     *
     * <pre>{@code
     * SELECT *
     * FROM booking_checkouts
     * WHERE status IN ('DRAFT', 'HELD', 'PAYMENT_PENDING', 'HOST_PENDING')
     *   AND expires_at <= :decisionInstant
     * ORDER BY expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Correctness never depends on a browser reporting that the guest went away. An abandoned
     * attempt is found here, by its deadline, or it is not found at all.</p>
     *
     * @param decisionInstant the sweep's single decision instant
     * @param batchSize maximum number of attempts to return
     * @return possibly empty list, most overdue first
     */
    @Query("""
            SELECT *
            FROM booking_checkouts
            WHERE status IN ('DRAFT', 'HELD', 'PAYMENT_PENDING', 'HOST_PENDING')
              AND expires_at <= :decisionInstant
            ORDER BY expires_at
            LIMIT :batchSize
            """)
    List<BookingCheckout> findLapsed(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);

    /**
     * Returns a guest's attempts, most recent first.
     *
     * <p>Spring derives {@code WHERE guest_account_holder_id = ? ORDER BY created_at DESC}.</p>
     *
     * @param guestAccountHolderId guest whose attempts are wanted
     * @return possibly empty list of attempts
     */
    List<BookingCheckout> findAllByGuestAccountHolderIdOrderByCreatedAtDesc(
            UUID guestAccountHolderId);
}
