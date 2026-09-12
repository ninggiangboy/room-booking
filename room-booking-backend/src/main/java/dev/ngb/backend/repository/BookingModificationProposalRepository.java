package dev.ngb.backend.repository;

import dev.ngb.backend.model.BookingModificationProposal;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads proposed changes to live booking contracts.
 *
 * <p>At most one proposal per booking is live, so the live read returns an {@code Optional} rather than
 * a list -- {@code uk_booking_modification_proposals_open} is what makes that safe.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code booking_modification_proposals}.</p>
 */
public interface BookingModificationProposalRepository extends ListCrudRepository<BookingModificationProposal, UUID> {

    /**
     * Returns the booking's live proposal, if it has one.
     *
     * <pre>{@code
     * SELECT *
     * FROM booking_modification_proposals
     * WHERE booking_id = :bookingId AND status IN ('OPEN', 'ACCEPTED')
     * }</pre>
     *
     * <p>At most one row can match: the partial unique index covers exactly this predicate. Two open
     * proposals would each hold different nights and each believe they describe the next revision.</p>
     *
     * @param bookingId booking to check
     * @return the live proposal, when one exists
     */
    @Query("""
            SELECT *
            FROM booking_modification_proposals
            WHERE booking_id = :bookingId AND status IN ('OPEN', 'ACCEPTED')
            """)
    Optional<BookingModificationProposal> findLive(@Param("bookingId") UUID bookingId);

    /**
     * Finds the proposal a retried command already opened.
     *
     * <p>Spring derives {@code WHERE booking_id = ? AND idempotency_key = ?}, matching
     * {@code uk_booking_modification_proposals_idempotency}.</p>
     *
     * @param bookingId booking the command targeted
     * @param idempotencyKey client key
     * @return the proposal, when one exists
     */
    Optional<BookingModificationProposal> findByBookingIdAndIdempotencyKey(
            UUID bookingId, String idempotencyKey);

    /**
     * Returns live proposals whose window has closed.
     *
     * <pre>{@code
     * SELECT *
     * FROM booking_modification_proposals
     * WHERE status IN ('OPEN', 'ACCEPTED') AND expires_at <= :at
     * ORDER BY expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>The sweeper's read. Expiring a proposal is what releases the inventory hold it was keeping, so a
     * stalled negotiation does not take nights off the market indefinitely.</p>
     *
     * @param at instant to judge expiry against
     * @param batchSize maximum rows to return
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM booking_modification_proposals
            WHERE status IN ('OPEN', 'ACCEPTED') AND expires_at <= :at
            ORDER BY expires_at
            LIMIT :batchSize
            """)
    List<BookingModificationProposal> findExpired(
            @Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Locks one proposal for update.
     *
     * <pre>{@code SELECT * FROM booking_modification_proposals WHERE id = :id FOR UPDATE}</pre>
     *
     * <p>Must be called inside a transaction. Taken before committing a proposal, so that a guest
     * accepting and a sweeper expiring cannot both act on the same row.</p>
     *
     * @param id proposal to lock
     * @return the locked proposal, when it exists
     */
    @Query("SELECT * FROM booking_modification_proposals WHERE id = :id FOR UPDATE")
    Optional<BookingModificationProposal> findByIdForUpdate(@Param("id") UUID id);
}
