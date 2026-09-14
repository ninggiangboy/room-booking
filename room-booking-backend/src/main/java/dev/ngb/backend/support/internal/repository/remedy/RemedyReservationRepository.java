package dev.ngb.backend.support.internal.repository.remedy;

import dev.ngb.backend.support.internal.model.remedy.RemedyReservation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.remedy.RemedyReservation;


/**
 * Reads and holds reservations against source ceilings.
 *
 * <p>The held sum is what makes the cumulative rule transactional: two agents reading a remaining
 * entitlement a second apart must not both see the whole of it.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code remedy_reservations}.</p>
 */
public interface RemedyReservationRepository extends ListCrudRepository<RemedyReservation, UUID> {

    /**
     * Sums what is currently held against one ceiling.
     *
     * <pre>{@code
     * SELECT coalesce(sum(reserved_amount_minor), 0)
     * FROM remedy_reservations
     * WHERE ceiling_scope = :ceilingScope
     *   AND scope_key = :scopeKey
     *   AND currency = :currency
     *   AND state = 'HELD'
     * }</pre>
     *
     * <p>Callers take the ceiling row under lock before reading this, because a sum read outside a lock
     * is a figure that was true a moment ago.</p>
     *
     * @param ceilingScope scope the ceiling is measured over
     * @param scopeKey the thing it belongs to
     * @param currency currency of the ceiling
     * @return total held in minor units, zero when nothing is held
     */
    @Query("""
            SELECT coalesce(sum(reserved_amount_minor), 0)
            FROM remedy_reservations
            WHERE ceiling_scope = :ceilingScope
              AND scope_key = :scopeKey
              AND currency = :currency
              AND state = 'HELD'
            """)
    long sumHeld(@Param("ceilingScope") String ceilingScope, @Param("scopeKey") String scopeKey,
            @Param("currency") String currency);

    /**
     * Claims reservations that have run out.
     *
     * <pre>{@code
     * SELECT *
     * FROM remedy_reservations
     * WHERE state = 'HELD' AND expires_at <= :at
     * ORDER BY expires_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. A hold nobody released is a permanent reduction of somebody
     * else’s entitlement, which is why it expires rather than waiting for an agent to return.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum reservations to claim
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM remedy_reservations
            WHERE state = 'HELD' AND expires_at <= :at
            ORDER BY expires_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<RemedyReservation> claimExpired(@Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Lists the reservations taken on a case.
     *
     * @param supportCaseId case
     * @return possibly empty list
     */
    List<RemedyReservation> findBySupportCaseId(UUID supportCaseId);
}
