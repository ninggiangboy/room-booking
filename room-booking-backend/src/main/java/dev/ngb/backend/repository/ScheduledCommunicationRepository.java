package dev.ngb.backend.repository;

import dev.ngb.backend.model.ScheduledCommunication;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads reminders that have not fired yet.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code scheduled_communications}.</p>
 */
public interface ScheduledCommunicationRepository extends ListCrudRepository<ScheduledCommunication, UUID> {

    /**
     * Claims jobs whose resolved instant has arrived.
     *
     * <pre>{@code
     * SELECT *
     * FROM scheduled_communications
     * WHERE state = 'SCHEDULED'
     *   AND resolved_run_at <= :at
     * ORDER BY resolved_run_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. The worker revalidates the anchor version after claiming and
     * before creating an intent, so a job made stale by a modification is discarded rather than sent.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum jobs to claim
     * @return possibly empty list, most overdue first
     */
    @Query("""
            SELECT *
            FROM scheduled_communications
            WHERE state = 'SCHEDULED'
              AND resolved_run_at <= :at
            ORDER BY resolved_run_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<ScheduledCommunication> claimDue(@Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Finds the live job holding a supersession key.
     *
     * <pre>{@code
     * SELECT *
     * FROM scheduled_communications
     * WHERE supersession_key = :supersessionKey
     *   AND state IN ('SCHEDULED', 'CLAIMED')
     * }</pre>
     *
     * <p>Matches {@code uk_scheduled_communications_live}, so at most one row can come back. A booking
     * modification reads it here and marks it superseded in the same transaction as it writes the
     * replacement.</p>
     *
     * @param supersessionKey identity a replacement would supersede
     * @return the live job, when one exists
     */
    @Query("""
            SELECT *
            FROM scheduled_communications
            WHERE supersession_key = :supersessionKey
              AND state IN ('SCHEDULED', 'CLAIMED')
            """)
    Optional<ScheduledCommunication> findLive(@Param("supersessionKey") String supersessionKey);

    /**
     * Returns the jobs hanging from one aggregate.
     *
     * <p>Spring derives {@code WHERE anchor_domain = ? AND anchor_aggregate_id = ?}. This is the read a
     * modification uses to find everything it has to supersede.</p>
     *
     * @param anchorDomain domain the anchor belongs to
     * @param anchorAggregateId the anchoring aggregate
     * @return possibly empty list
     */
    List<ScheduledCommunication> findAllByAnchorDomainAndAnchorAggregateId(String anchorDomain,
                                                                           UUID anchorAggregateId);
}
