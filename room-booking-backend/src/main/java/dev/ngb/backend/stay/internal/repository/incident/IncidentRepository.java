package dev.ngb.backend.stay.internal.repository.incident;

import dev.ngb.backend.stay.internal.model.incident.Incident;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads incidents raised during stays.
 *
 * <p>The lock is taken before any transition, because the incident allocates its own timeline
 * sequence. The safety queue is read separately from everything else, on purpose.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code incidents}.</p>
 */
public interface IncidentRepository extends ListCrudRepository<Incident, UUID> {

    /**
     * Locks one incident for a transition.
     *
     * <pre>{@code
     * SELECT * FROM incidents WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction. Every transition takes this lock, allocates the next timeline
     * sequence from the row, writes the event, and bumps the allocator in one go.</p>
     *
     * @param id incident to lock
     * @return the locked incident, when it exists
     */
    @Query("SELECT * FROM incidents WHERE id = :id FOR UPDATE")
    Optional<Incident> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Lists open incidents in urgency order.
     *
     * <pre>{@code
     * SELECT *
     * FROM incidents
     * WHERE state NOT IN ('RESOLVED', 'CLOSED', 'DUPLICATE', 'TRANSFERRED')
     * ORDER BY severity_rank, slo_target_at NULLS LAST
     * LIMIT :limit
     * }</pre>
     *
     * <p>Matches {@code idx_incidents_queue}. Ordering by the rank rather than the severity string is
     * why the rank column exists.</p>
     *
     * @param limit maximum incidents to return
     * @return possibly empty list, most urgent first
     */
    @Query("""
            SELECT *
            FROM incidents
            WHERE state NOT IN ('RESOLVED', 'CLOSED', 'DUPLICATE', 'TRANSFERRED')
            ORDER BY severity_rank, slo_target_at NULLS LAST
            LIMIT :limit
            """)
    List<Incident> findQueue(@Param("limit") int limit);

    /**
     * Lists open incidents whose response obligation has passed.
     *
     * <pre>{@code
     * SELECT *
     * FROM incidents
     * WHERE state NOT IN ('RESOLVED', 'CLOSED', 'DUPLICATE', 'TRANSFERRED')
     *   AND slo_target_at <= :at
     * ORDER BY slo_target_at
     * }</pre>
     *
     * <p>The instant is bound by the caller because an index predicate may not read the clock.</p>
     *
     * @param at instant to treat as now
     * @return possibly empty list, longest breaching first
     */
    @Query("""
            SELECT *
            FROM incidents
            WHERE state NOT IN ('RESOLVED', 'CLOSED', 'DUPLICATE', 'TRANSFERRED')
              AND slo_target_at <= :at
            ORDER BY slo_target_at
            """)
    List<Incident> findBreachingSlo(@Param("at") Instant at);

    /**
     * Lists open safety incidents, most urgent first.
     *
     * <pre>{@code
     * SELECT *
     * FROM incidents
     * WHERE safety_flag
     *   AND state NOT IN ('RESOLVED', 'CLOSED', 'DUPLICATE', 'TRANSFERRED')
     * ORDER BY severity_rank, created_at
     * }</pre>
     *
     * <p>Read separately because safety routing is deliberate rather than a filter on a general queue,
     * and because automation is restricted while one of these is open.</p>
     *
     * @return possibly empty list
     */
    @Query("""
            SELECT *
            FROM incidents
            WHERE safety_flag
              AND state NOT IN ('RESOLVED', 'CLOSED', 'DUPLICATE', 'TRANSFERRED')
            ORDER BY severity_rank, created_at
            """)
    List<Incident> findOpenSafety();

    /**
     * Lists incidents raised against a booking, newest first.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY created_at DESC}.</p>
     *
     * @param bookingId booking
     * @return possibly empty list
     */
    List<Incident> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);
}
