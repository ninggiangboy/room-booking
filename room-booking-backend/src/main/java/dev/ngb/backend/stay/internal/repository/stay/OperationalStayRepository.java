package dev.ngb.backend.stay.internal.repository.stay;

import dev.ngb.backend.stay.internal.model.stay.OperationalStay;
import dev.ngb.backend.stay.internal.model.stay.OperationalStayStatus;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.stay.internal.model.stay.OperationalStay;
import dev.ngb.backend.stay.internal.model.stay.OperationalStayStatus;


/**
 * Reads the operational view of bookings.
 *
 * <p>The live lookup is the entry point for every arrival, readiness and completion question; the
 * claim query is what the completion worker runs.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code operational_stays}.</p>
 */
public interface OperationalStayRepository extends ListCrudRepository<OperationalStay, UUID> {

    /**
     * Finds the operational view currently in force for a booking.
     *
     * <p>Spring derives {@code WHERE booking_id = ? AND status = ?}, which matches
     * {@code uk_operational_stays_live} when the status is {@code ACTIVE}. At most one such row can
     * exist, so an optional is honest here.</p>
     *
     * @param bookingId booking to look up
     * @param status status to match, normally {@code ACTIVE}
     * @return the live stay, when there is one
     */
    Optional<OperationalStay> findByBookingIdAndStatus(UUID bookingId, OperationalStayStatus status);

    /**
     * Finds the stay built for one booking revision.
     *
     * <p>Spring derives {@code WHERE booking_id = ? AND booking_revision_id = ?}, matching
     * {@code uk_operational_stays_revision}. Used to make stay creation idempotent.</p>
     *
     * @param bookingId booking
     * @param bookingRevisionId committed revision
     * @return the stay for that revision, when it exists
     */
    Optional<OperationalStay> findByBookingIdAndBookingRevisionId(UUID bookingId, UUID bookingRevisionId);

    /**
     * Claims live stays whose checkout plus grace has passed.
     *
     * <pre>{@code
     * SELECT *
     * FROM operational_stays
     * WHERE status = 'ACTIVE'
     *   AND outcome_proposal = 'PENDING'
     *   AND check_out_instant <= :dueBefore
     * ORDER BY check_out_instant
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. The caller subtracts the configured grace and passes an
     * instant, because an index predicate may not read the clock.</p>
     *
     * @param dueBefore checkout instant on or before which a stay is due for evaluation
     * @param batchSize maximum stays to claim
     * @return possibly empty list, oldest checkout first
     */
    @Query("""
            SELECT *
            FROM operational_stays
            WHERE status = 'ACTIVE'
              AND outcome_proposal = 'PENDING'
              AND check_out_instant <= :dueBefore
            ORDER BY check_out_instant
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<OperationalStay> claimDueForCompletion(@Param("dueBefore") Instant dueBefore,
            @Param("batchSize") int batchSize);

    /**
     * Locks one stay for a state change.
     *
     * <pre>{@code
     * SELECT * FROM operational_stays WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction. The canonical lock order within this domain is the stay first,
     * then its children, then the outbox.</p>
     *
     * @param id stay to lock
     * @return the locked stay, when it exists
     */
    @Query("SELECT * FROM operational_stays WHERE id = :id FOR UPDATE")
    Optional<OperationalStay> findByIdForUpdate(@Param("id") UUID id);
}
