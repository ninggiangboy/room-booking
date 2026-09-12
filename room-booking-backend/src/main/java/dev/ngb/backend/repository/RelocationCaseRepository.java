package dev.ngb.backend.repository;

import dev.ngb.backend.model.RelocationCase;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads cases for guests who must be moved.
 *
 * <p>The deadline read is the one that matters operationally: a case past its resolution time is a
 * guest standing outside a door.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code relocation_cases}.</p>
 */
public interface RelocationCaseRepository extends ListCrudRepository<RelocationCase, UUID> {

    /**
     * Returns unresolved cases, most urgent first.
     *
     * <pre>{@code
     * SELECT *
     * FROM relocation_cases
     * WHERE state IN ('OPEN', 'OFFERING', 'ACCEPTED')
     * ORDER BY resolution_due_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Covered by {@code idx_relocation_cases_due}. The predicate names no time function, so the index
     * keeps matching the rows it was built for as the clock moves.</p>
     *
     * @param batchSize maximum rows to return
     * @return possibly empty list, soonest deadline first
     */
    @Query("""
            SELECT *
            FROM relocation_cases
            WHERE state IN ('OPEN', 'OFFERING', 'ACCEPTED')
            ORDER BY resolution_due_at
            LIMIT :batchSize
            """)
    List<RelocationCase> findUnresolved(@Param("batchSize") int batchSize);

    /**
     * Returns a booking's relocation cases, most recent first.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY created_at DESC}.</p>
     *
     * @param bookingId booking whose cases are wanted
     * @return possibly empty list, newest first
     */
    List<RelocationCase> findAllByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /**
     * Locks one case for update.
     *
     * <pre>{@code SELECT * FROM relocation_cases WHERE id = :id FOR UPDATE}</pre>
     *
     * <p>Must be called inside a transaction. Taken before approving spend, so that two agents working the
     * same case at two in the morning cannot both approve against the same remaining budget.</p>
     *
     * @param id case to lock
     * @return the locked case, when it exists
     */
    @Query("SELECT * FROM relocation_cases WHERE id = :id FOR UPDATE")
    Optional<RelocationCase> findByIdForUpdate(@Param("id") UUID id);
}
