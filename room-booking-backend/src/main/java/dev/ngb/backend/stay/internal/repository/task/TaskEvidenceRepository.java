package dev.ngb.backend.stay.internal.repository.task;

import dev.ngb.backend.stay.internal.model.task.TaskEvidence;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.stay.internal.model.task.TaskEvidence;


/**
 * Reads what was submitted in support of task attestations.
 *
 * <p>Frozen at insert except for scan state, classification and legal hold, so this interface reads,
 * appends, and updates only those.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code task_evidence}.</p>
 */
public interface TaskEvidenceRepository extends ListCrudRepository<TaskEvidence, UUID> {

    /**
     * Lists what was submitted for one task, newest first.
     *
     * <p>Spring derives {@code WHERE operational_task_id = ? ORDER BY received_at DESC}.</p>
     *
     * @param operationalTaskId task
     * @return possibly empty list, most recent first
     */
    List<TaskEvidence> findByOperationalTaskIdOrderByReceivedAtDesc(UUID operationalTaskId);

    /**
     * Counts the evidence that may support a completion.
     *
     * <pre>{@code
     * SELECT count(*)
     * FROM task_evidence
     * WHERE operational_task_id = :operationalTaskId
     *   AND scan_state IN ('CLEAN', 'NOT_APPLICABLE')
     * }</pre>
     *
     * <p>The same test the completion trigger applies. A service that checks this first gets a clear
     * message instead of a constraint violation, but the database still has the last word.</p>
     *
     * @param operationalTaskId task
     * @return number of scanned-clear items
     */
    @Query("""
            SELECT count(*)
            FROM task_evidence
            WHERE operational_task_id = :operationalTaskId
              AND scan_state IN ('CLEAN', 'NOT_APPLICABLE')
            """)
    long countUsable(@Param("operationalTaskId") UUID operationalTaskId);

    /**
     * Claims uploads waiting to be scanned.
     *
     * <pre>{@code
     * SELECT *
     * FROM task_evidence
     * WHERE scan_state IN ('PENDING', 'FAILED')
     * ORDER BY received_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. {@code SKIP LOCKED} lets several scanners share the queue.</p>
     *
     * @param batchSize maximum items to claim
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM task_evidence
            WHERE scan_state IN ('PENDING', 'FAILED')
            ORDER BY received_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<TaskEvidence> claimPendingScans(@Param("batchSize") int batchSize);
}
