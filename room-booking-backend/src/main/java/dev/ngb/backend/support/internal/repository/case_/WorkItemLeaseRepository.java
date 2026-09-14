package dev.ngb.backend.support.internal.repository.case_;

import dev.ngb.backend.support.internal.model.case_.WorkItemLease;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.case_.WorkItemLease;


/**
 * Reads the leases held on work items.
 *
 * <p>A lease is never updated once released, so the live lookup and the expiry sweep are the only
 * two ways in.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code work_item_leases}.</p>
 */
public interface WorkItemLeaseRepository extends ListCrudRepository<WorkItemLease, UUID> {

    /**
     * Finds the lease currently held on a work item.
     *
     * <pre>{@code
     * SELECT * FROM work_item_leases WHERE case_work_item_id = :caseWorkItemId AND released_at IS NULL
     * }</pre>
     *
     * <p>Matches {@code uk_work_item_leases_live}, so at most one row can come back.</p>
     *
     * @param caseWorkItemId work item
     * @return the live lease, when one is held
     */
    @Query("SELECT * FROM work_item_leases WHERE case_work_item_id = :caseWorkItemId AND released_at IS NULL")
    Optional<WorkItemLease> findLive(@Param("caseWorkItemId") UUID caseWorkItemId);

    /**
     * Claims leases that have run out, so their work can return to the queue.
     *
     * <pre>{@code
     * SELECT *
     * FROM work_item_leases
     * WHERE released_at IS NULL AND expires_at <= :at
     * ORDER BY expires_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. Releasing an expired lease returns the work; it does not
     * change case ownership and it does not discard the holder’s draft.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum leases to claim
     * @return possibly empty list, longest expired first
     */
    @Query("""
            SELECT *
            FROM work_item_leases
            WHERE released_at IS NULL AND expires_at <= :at
            ORDER BY expires_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<WorkItemLease> claimExpired(@Param("at") Instant at, @Param("batchSize") int batchSize);
}
