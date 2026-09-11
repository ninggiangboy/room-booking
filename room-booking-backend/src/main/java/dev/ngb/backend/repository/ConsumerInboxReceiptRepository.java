package dev.ngb.backend.repository;

import dev.ngb.backend.model.ConsumerInboxReceipt;
import dev.ngb.backend.model.ConsumerInboxReceiptId;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Records that a consumer has applied an event's effect, at most once.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} operate on the composite
 * {@link ConsumerInboxReceiptId} key and the {@code consumer_inbox_receipts} table.</p>
 */
public interface ConsumerInboxReceiptRepository
        extends ListCrudRepository<ConsumerInboxReceipt, ConsumerInboxReceiptId> {

    /**
     * Claims an event for a consumer, doing nothing if that consumer has already seen it.
     *
     * <p>Explicit SQL is required because the table has a composite primary key and because the
     * conflict clause is the deduplication itself:</p>
     *
     * <pre>{@code
     * INSERT INTO consumer_inbox_receipts
     *     (consumer_name, event_id, consumer_version, schema_version, first_seen_at, state)
     * VALUES (:consumerName, :eventId, :consumerVersion, :schemaVersion, :firstSeenAt, 'PENDING')
     * ON CONFLICT (consumer_name, event_id) DO NOTHING
     * }</pre>
     *
     * <p>A return of one means this delivery is the first and the handler should run. A return of
     * zero means the event was already seen, and the handler must not run again — which is what
     * makes a transport that delivers twice harmless.</p>
     *
     * @param consumerName stable name of the consumer
     * @param eventId identifier of the delivered event
     * @param consumerVersion handler version, recorded for diagnostics
     * @param schemaVersion payload schema version the handler interpreted
     * @param firstSeenAt the consumer's decision instant for this delivery
     * @return number of inserted rows, either zero or one
     */
    @Modifying
    @Query("""
            INSERT INTO consumer_inbox_receipts
                (consumer_name, event_id, consumer_version, schema_version, first_seen_at, state)
            VALUES (:consumerName, :eventId, :consumerVersion, :schemaVersion, :firstSeenAt, 'PENDING')
            ON CONFLICT (consumer_name, event_id) DO NOTHING
            """)
    int claimFirstDelivery(
            @Param("consumerName") String consumerName,
            @Param("eventId") UUID eventId,
            @Param("consumerVersion") short consumerVersion,
            @Param("schemaVersion") short schemaVersion,
            @Param("firstSeenAt") Instant firstSeenAt);

    /**
     * Finds one consumer's receipt for one event.
     *
     * <p>Spring derives the predicate from the composite-key property path:</p>
     *
     * <pre>{@code
     * SELECT ...
     * FROM consumer_inbox_receipts
     * WHERE consumer_name = ?
     *   AND event_id = ?
     * }</pre>
     *
     * @param consumerName stable name of the consumer
     * @param eventId identifier of the event
     * @return the receipt when this consumer has seen the event
     */
    Optional<ConsumerInboxReceipt> findByIdConsumerNameAndIdEventId(
            String consumerName,
            UUID eventId);

    /**
     * Returns one consumer's unfinished backlog, oldest first.
     *
     * <pre>{@code
     * SELECT *
     * FROM consumer_inbox_receipts
     * WHERE consumer_name = :consumerName
     *   AND state IN ('PENDING', 'FAILED')
     * ORDER BY first_seen_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>The bound is what keeps a consumer that has fallen far behind from loading its whole
     * backlog into memory in one pass.</p>
     *
     * @param consumerName stable name of the consumer
     * @param batchSize maximum number of receipts to return
     * @return possibly empty list of unfinished receipts
     */
    @Query("""
            SELECT *
            FROM consumer_inbox_receipts
            WHERE consumer_name = :consumerName
              AND state IN ('PENDING', 'FAILED')
            ORDER BY first_seen_at
            LIMIT :batchSize
            """)
    List<ConsumerInboxReceipt> findBacklog(
            @Param("consumerName") String consumerName,
            @Param("batchSize") int batchSize);
}
