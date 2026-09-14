package dev.ngb.backend.review.internal.repository.right;

import dev.ngb.backend.review.internal.model.right.ReviewCycle;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the disclosure coordinators.
 *
 * <p>The claim query is the reveal worker's entry point, and the lock is taken before any state
 * change because the cycle issues its reveal epoch exactly once.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code review_cycles}.</p>
 */
public interface ReviewCycleRepository extends ListCrudRepository<ReviewCycle, UUID> {

    /**
     * Finds the live cycle for a booking.
     *
     * <pre>{@code
     * SELECT * FROM review_cycles WHERE booking_id = :bookingId AND state <> 'SUPERSEDED'
     * }</pre>
     *
     * <p>Matches {@code uk_review_cycles_live}, so at most one row can come back.</p>
     *
     * @param bookingId booking
     * @return the live cycle, when there is one
     */
    @Query("SELECT * FROM review_cycles WHERE booking_id = :bookingId AND state <> 'SUPERSEDED'")
    Optional<ReviewCycle> findLiveByBooking(@Param("bookingId") UUID bookingId);

    /**
     * Claims cycles whose submission window has closed.
     *
     * <pre>{@code
     * SELECT *
     * FROM review_cycles
     * WHERE state IN ('OPEN', 'ONE_SIDED_SEALED')
     *   AND submission_deadline_at <= :at
     * ORDER BY submission_deadline_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. A job running late still reads the snapshotted deadline, so the
     * original semantics survive the delay.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum cycles to claim
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT *
            FROM review_cycles
            WHERE state IN ('OPEN', 'ONE_SIDED_SEALED')
              AND submission_deadline_at <= :at
            ORDER BY submission_deadline_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<ReviewCycle> claimDueForReveal(@Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Claims cycles whose moderation hold has run out.
     *
     * <pre>{@code
     * SELECT *
     * FROM review_cycles
     * WHERE state = 'REVEALING'
     *   AND maximum_hold_expires_at <= :at
     * ORDER BY maximum_hold_expires_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. At this point each independently qualified review publishes and
     * the other stays hidden: an innocent party's review must not be held indefinitely because their
     * counterpart's is in moderation.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum cycles to claim
     * @return possibly empty list, longest held first
     */
    @Query("""
            SELECT *
            FROM review_cycles
            WHERE state = 'REVEALING'
              AND maximum_hold_expires_at <= :at
            ORDER BY maximum_hold_expires_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<ReviewCycle> claimExpiredHolds(@Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Locks one cycle for a disclosure change.
     *
     * <pre>{@code
     * SELECT * FROM review_cycles WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction. Every reveal takes this lock, because two workers issuing
     * separate epochs is precisely what the design must not allow.</p>
     *
     * @param id cycle to lock
     * @return the locked cycle, when it exists
     */
    @Query("SELECT * FROM review_cycles WHERE id = :id FOR UPDATE")
    Optional<ReviewCycle> findByIdForUpdate(@Param("id") UUID id);
}
