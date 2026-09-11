package dev.ngb.backend.repository;

import dev.ngb.backend.model.CommandIdempotencyRecord;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and recovers retry-safety records for commands.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select, insert/update,
 * and delete SQL for {@code command_idempotency_records}.</p>
 */
public interface CommandIdempotencyRecordRepository
        extends ListCrudRepository<CommandIdempotencyRecord, UUID> {

    /**
     * Finds the record for one idempotency key, which is how a replay is detected.
     *
     * <p>Spring splits the property path at each {@code And} and produces four equality
     * predicates, matching the {@code uk_command_idempotency_records_scope} unique constraint:</p>
     *
     * <pre>{@code
     * SELECT ...
     * FROM command_idempotency_records
     * WHERE scope_type = ?
     *   AND scope_key = ?
     *   AND operation = ?
     *   AND key_digest = ?
     * }</pre>
     *
     * <p>Returning {@link Optional} expresses that at most one row can match; more than one would
     * mean the unique constraint had been dropped.</p>
     *
     * @param scopeType kind of scope the key is unique within
     * @param scopeKey identifier of that scope
     * @param operation command being made retry-safe
     * @param keyDigest SHA-256 digest of the caller-supplied key
     * @return the existing record when this key has been seen before
     */
    Optional<CommandIdempotencyRecord> findByScopeTypeAndScopeKeyAndOperationAndKeyDigest(
            String scopeType,
            String scopeKey,
            String operation,
            String keyDigest);

    /**
     * Finds commands whose execution lease has lapsed, so a worker can recover them.
     *
     * <p>This method is not name-derived, because the caller must bind its own decision instant
     * rather than let the database read a second clock:</p>
     *
     * <pre>{@code
     * SELECT ...
     * FROM command_idempotency_records
     * WHERE state = 'IN_PROGRESS'
     *   AND lease_expires_at <= :decisionInstant
     * ORDER BY lease_expires_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>A lapsed lease permits recovery, never the assumption that no effect occurred: the command
     * may have committed and then crashed before settling its record. The batch is bounded so one
     * recovery pass cannot load an unbounded backlog.</p>
     *
     * @param decisionInstant the recovery pass's single decision instant
     * @param batchSize maximum number of records to claim
     * @return possibly empty list of recoverable records, oldest lease first
     */
    @Query("""
            SELECT *
            FROM command_idempotency_records
            WHERE state = 'IN_PROGRESS'
              AND lease_expires_at <= :decisionInstant
            ORDER BY lease_expires_at
            LIMIT :batchSize
            """)
    List<CommandIdempotencyRecord> findRecoverable(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);
}
