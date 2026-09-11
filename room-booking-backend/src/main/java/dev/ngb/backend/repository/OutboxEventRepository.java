package dev.ngb.backend.repository;

import dev.ngb.backend.model.OutboxEvent;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stores domain facts transactionally and hands them to the publisher in bounded batches.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code outbox_events}.</p>
 */
public interface OutboxEventRepository extends ListCrudRepository<OutboxEvent, UUID> {

    /**
     * Claims a bounded batch of publishable facts for one publisher instance.
     *
     * <p>{@code @Query} supplies the SQL because name derivation cannot express
     * {@code FOR UPDATE SKIP LOCKED}, which is what lets several publisher instances run
     * concurrently without contending on the same rows:</p>
     *
     * <pre>{@code
     * SELECT *
     * FROM outbox_events
     * WHERE publication_state IN ('PENDING', 'FAILED')
     *   AND available_at <= :decisionInstant
     * ORDER BY available_at, id
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>The caller binds its own decision instant rather than letting the database read a second
     * clock. This method must run inside a transaction; the rows stay locked until it commits, and
     * the caller is expected to move each claimed row to {@code PUBLISHING} under that lock.</p>
     *
     * @param decisionInstant the publication pass's single decision instant
     * @param batchSize maximum number of facts to claim
     * @return possibly empty list of claimed facts, oldest availability first
     */
    @Query("""
            SELECT *
            FROM outbox_events
            WHERE publication_state IN ('PENDING', 'FAILED')
              AND available_at <= :decisionInstant
            ORDER BY available_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<OutboxEvent> claimPublishable(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);

    /**
     * Finds facts whose publication lease has lapsed, so another instance can take them.
     *
     * <pre>{@code
     * SELECT *
     * FROM outbox_events
     * WHERE publication_state = 'PUBLISHING'
     *   AND lease_expires_at <= :decisionInstant
     * ORDER BY lease_expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Reclaiming may republish a fact that was already delivered. That is deliberate: the
     * platform guarantees at-least-once publication and relies on
     * {@code consumer_inbox_receipts} for at-most-once effect.</p>
     *
     * @param decisionInstant the recovery pass's single decision instant
     * @param batchSize maximum number of facts to reclaim
     * @return possibly empty list of abandoned facts, oldest lease first
     */
    @Query("""
            SELECT *
            FROM outbox_events
            WHERE publication_state = 'PUBLISHING'
              AND lease_expires_at <= :decisionInstant
            ORDER BY lease_expires_at
            LIMIT :batchSize
            """)
    List<OutboxEvent> findAbandoned(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);

    /**
     * Returns the recorded facts for one aggregate in the order they occurred.
     *
     * <p>Spring derives {@code WHERE aggregate_type = ? AND aggregate_id = ?} from the property
     * path and appends {@code ORDER BY occurred_at ASC} from the {@code OrderBy} suffix. Used for
     * diagnosing why a downstream projection disagrees with the aggregate.</p>
     *
     * @param aggregateType kind of aggregate
     * @param aggregateId identifier of that aggregate
     * @return possibly empty list of facts, oldest first
     */
    List<OutboxEvent> findAllByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(
            String aggregateType,
            UUID aggregateId);
}
