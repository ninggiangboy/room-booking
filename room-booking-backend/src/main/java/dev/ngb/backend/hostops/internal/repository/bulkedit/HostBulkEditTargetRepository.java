package dev.ngb.backend.hostops.internal.repository.bulkedit;

import dev.ngb.backend.hostops.internal.model.bulkedit.HostBulkEditTarget;
import dev.ngb.backend.hostops.internal.model.bulkedit.BulkEditTargetOutcome;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.ngb.backend.hostops.internal.model.bulkedit.BulkEditTargetOutcome;
import dev.ngb.backend.hostops.internal.model.bulkedit.HostBulkEditTarget;


/**
 * Reads what actually happened to each night, listing or rate plan in a bulk edit.
 *
 * <p>Rows are append-only. A target that was refused stays refused in the record, because the
 * whole point of the table is that the host can see which of four hundred nights did not move.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_bulk_edit_targets}.</p>
 */
public interface HostBulkEditTargetRepository extends ListCrudRepository<HostBulkEditTarget, UUID> {

    /**
     * Lists one edit's targets in the order they were attempted.
     *
     * @param bulkEditRequestId the request
     * @return possibly empty list, in attempt order
     */
    List<HostBulkEditTarget> findByBulkEditRequestIdOrderBySequenceNumber(UUID bulkEditRequestId);

    /**
     * Lists the targets of one edit with one outcome.
     *
     * @param bulkEditRequestId the request
     * @param outcome what happened to the target
     * @return possibly empty list
     */
    List<HostBulkEditTarget> findByBulkEditRequestIdAndOutcome(UUID bulkEditRequestId,
            BulkEditTargetOutcome outcome);

    /**
     * Counts one edit's targets by outcome, which is what the summary shown to the host is built
     * from.
     *
     * <pre>{@code
     * SELECT outcome AS outcome, count(*) AS targetCount
     * FROM host_bulk_edit_targets
     * WHERE bulk_edit_request_id = :bulkEditRequestId
     * GROUP BY outcome
     * }</pre>
     *
     * @param bulkEditRequestId the request
     * @return one row per outcome the edit produced
     */
    @Query("""
            SELECT outcome AS outcome, count(*) AS targetCount
            FROM host_bulk_edit_targets
            WHERE bulk_edit_request_id = :bulkEditRequestId
            GROUP BY outcome
            """)
    List<OutcomeCount> countByOutcome(@Param("bulkEditRequestId") UUID bulkEditRequestId);

    /**
     * Counts the reasons targets were refused across a window, which is what tells a supply team
     * whether hosts keep being stopped by the same thing.
     *
     * <pre>{@code
     * SELECT reason_code AS reasonCode, count(*) AS targetCount
     * FROM host_bulk_edit_targets
     * WHERE outcome = 'REFUSED' AND attempted_at >= :since
     * GROUP BY reason_code
     * ORDER BY count(*) DESC
     * }</pre>
     *
     * @param since earliest attempt instant to include
     * @return one row per refusal reason, most frequent first
     */
    @Query("""
            SELECT reason_code AS reasonCode, count(*) AS targetCount
            FROM host_bulk_edit_targets
            WHERE outcome = 'REFUSED' AND attempted_at >= :since
            GROUP BY reason_code
            ORDER BY count(*) DESC
            """)
    List<RefusalCount> countRefusals(@Param("since") Instant since);

    /**
     * How many of one edit's targets ended in one outcome.
     *
     * @param outcome what happened to the target
     * @param targetCount how many targets did
     */
    record OutcomeCount(BulkEditTargetOutcome outcome, long targetCount) {}

    /**
     * How often one reason was given for refusing a target.
     *
     * @param reasonCode the approved reason code
     * @param targetCount how many targets it refused
     */
    record RefusalCount(String reasonCode, long targetCount) {}
}
