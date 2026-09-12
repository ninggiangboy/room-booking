package dev.ngb.backend.repository;

import dev.ngb.backend.model.ExternalReservation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads stays that other channels report against this platform's inventory.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code external_reservations}.</p>
 */
public interface ExternalReservationRepository extends ListCrudRepository<ExternalReservation, UUID> {

    /**
     * Finds a previously imported reservation by its external identity.
     *
     * <p>Spring derives {@code WHERE inventory_resource_id = ? AND external_source = ? AND
     * external_uid = ?}, matching {@code uk_external_reservations_uid}. This is what makes re-reading
     * the same feed idempotent rather than duplicating every event on each sync.</p>
     *
     * @param inventoryResourceId resource the reservation applies to
     * @param externalSource platform that reported it
     * @param externalUid identifier that platform assigned
     * @return the existing row when the event has been seen before
     */
    Optional<ExternalReservation> findByInventoryResourceIdAndExternalSourceAndExternalUid(
            UUID inventoryResourceId,
            String externalSource,
            String externalUid);

    /**
     * Returns conflicts waiting for a person to decide what happens.
     *
     * <pre>{@code
     * SELECT *
     * FROM external_reservations
     * WHERE conflict_state = 'DETECTED'
     * ORDER BY first_seen_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>These are nights another channel believes it sold that this platform has already sold.
     * Neither resolution is safe to automate — cancelling our booking or ignoring theirs both strand a
     * real guest — so the queue exists for a human to work, oldest first because the affected stay may
     * be imminent.</p>
     *
     * @param batchSize maximum number of conflicts to return
     * @return possibly empty conflict queue
     */
    @Query("""
            SELECT *
            FROM external_reservations
            WHERE conflict_state = 'DETECTED'
            ORDER BY first_seen_at
            LIMIT :batchSize
            """)
    List<ExternalReservation> findUnresolvedConflicts(@Param("batchSize") int batchSize);
}
