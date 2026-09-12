package dev.ngb.backend.repository;

import dev.ngb.backend.model.CollectionScheduleItem;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads the due components of a collection schedule.
 *
 * <p>Components of one schedule version must sum to their obligation, enforced by a deferred
 * constraint trigger. Writing a replacement schedule therefore means inserting the whole version in
 * one transaction.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code collection_schedule_items}.</p>
 */
public interface CollectionScheduleItemRepository extends ListCrudRepository<CollectionScheduleItem, UUID> {

    /**
     * Returns one schedule version in order.
     *
     * <p>Spring derives
     * {@code WHERE obligation_id = ? AND schedule_version = ? ORDER BY sequence_number}.</p>
     *
     * @param obligationId obligation whose schedule is wanted
     * @param scheduleVersion generation of the schedule
     * @return possibly empty list of components, in sequence
     */
    List<CollectionScheduleItem> findAllByObligationIdAndScheduleVersionOrderBySequenceNumber(
            UUID obligationId, int scheduleVersion);

    /**
     * Returns the current schedule for an obligation.
     *
     * <pre>{@code
     * SELECT *
     * FROM collection_schedule_items
     * WHERE obligation_id = :obligationId
     *   AND schedule_version = (
     *       SELECT max(schedule_version)
     *       FROM collection_schedule_items
     *       WHERE obligation_id = :obligationId)
     * ORDER BY sequence_number
     * }</pre>
     *
     * <p>The highest version is resolved in the database, so a caller cannot read a stale schedule by
     * asking for a version it happened to remember.</p>
     *
     * @param obligationId obligation whose current schedule is wanted
     * @return possibly empty list of components, in sequence
     */
    @Query("""
            SELECT *
            FROM collection_schedule_items
            WHERE obligation_id = :obligationId
              AND schedule_version = (
                  SELECT max(schedule_version)
                  FROM collection_schedule_items
                  WHERE obligation_id = :obligationId)
            ORDER BY sequence_number
            """)
    List<CollectionScheduleItem> findCurrentSchedule(@Param("obligationId") UUID obligationId);

    /**
     * Claims due components for the dunning worker.
     *
     * <pre>{@code
     * SELECT *
     * FROM collection_schedule_items
     * WHERE state IN ('OPEN', 'PROCESSING')
     *   AND next_attempt_at IS NOT NULL
     *   AND next_attempt_at <= :decisionInstant
     * ORDER BY next_attempt_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> {@code SKIP LOCKED} is what lets several
     * workers share the backlog without two of them collecting the same component.</p>
     *
     * @param decisionInstant the worker's single decision instant
     * @param batchSize maximum number of components to claim
     * @return possibly empty list of claimed components
     */
    @Query("""
            SELECT *
            FROM collection_schedule_items
            WHERE state IN ('OPEN', 'PROCESSING')
              AND next_attempt_at IS NOT NULL
              AND next_attempt_at <= :decisionInstant
            ORDER BY next_attempt_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<CollectionScheduleItem> claimDue(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);
}
