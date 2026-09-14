package dev.ngb.backend.support.internal.repository.case_;

import dev.ngb.backend.support.internal.model.case_.CaseWorkItem;
import dev.ngb.backend.support.internal.model.case_.WorkItemState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.case_.CaseWorkItem;
import dev.ngb.backend.support.internal.model.case_.WorkItemState;


/**
 * Reads and claims the work waiting on a queue.
 *
 * <p>The claim query is the worker and agent entry point; the lock is taken before the lease is
 * written, because two people believing they own one task is what the fencing token then has to
 * resolve after both have already acted.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code case_work_items}.</p>
 */
public interface CaseWorkItemRepository extends ListCrudRepository<CaseWorkItem, UUID> {

    /**
     * Claims ready work from a queue, most urgent first.
     *
     * <pre>{@code
     * SELECT *
     * FROM case_work_items
     * WHERE support_queue_id = :supportQueueId
     *   AND state = 'READY'
     * ORDER BY severity_rank, priority DESC, due_at NULLS LAST
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. Severity rank orders first and priority only within it, so a
     * high priority can never lift ordinary work above a safety-critical item.</p>
     *
     * @param supportQueueId queue to claim from
     * @param batchSize maximum items to claim
     * @return possibly empty list, most urgent first
     */
    @Query("""
            SELECT *
            FROM case_work_items
            WHERE support_queue_id = :supportQueueId
              AND state = 'READY'
            ORDER BY severity_rank, priority DESC, due_at NULLS LAST
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<CaseWorkItem> claimReady(@Param("supportQueueId") UUID supportQueueId,
            @Param("batchSize") int batchSize);

    /**
     * Claims work whose deadline has passed.
     *
     * <pre>{@code
     * SELECT *
     * FROM case_work_items
     * WHERE state IN ('READY', 'CLAIMED', 'IN_PROGRESS')
     *   AND due_at IS NOT NULL
     *   AND due_at <= :at
     * ORDER BY due_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum items to claim
     * @return possibly empty list, longest overdue first
     */
    @Query("""
            SELECT *
            FROM case_work_items
            WHERE state IN ('READY', 'CLAIMED', 'IN_PROGRESS')
              AND due_at IS NOT NULL
              AND due_at <= :at
            ORDER BY due_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<CaseWorkItem> claimOverdue(@Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Locks one work item before its lease or its completion changes.
     *
     * <pre>{@code
     * SELECT * FROM case_work_items WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction.</p>
     *
     * @param id work item to lock
     * @return the locked work item, when it exists
     */
    @Query("SELECT * FROM case_work_items WHERE id = :id FOR UPDATE")
    Optional<CaseWorkItem> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Lists the work one agent currently holds.
     *
     * @param ownerAccountHolderId agent
     * @param state work item state
     * @return possibly empty list
     */
    List<CaseWorkItem> findByOwnerAccountHolderIdAndState(UUID ownerAccountHolderId,
            WorkItemState state);
}
