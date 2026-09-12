package dev.ngb.backend.repository;

import dev.ngb.backend.model.PaymentOperation;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and claims the external actions requested of providers.
 *
 * <p>This is the auditable record of what was actually asked of a provider, so the reads here are
 * the ones that decide whether a retry is safe.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payment_operations}.</p>
 */
public interface PaymentOperationRepository extends ListCrudRepository<PaymentOperation, UUID> {

    /**
     * Loads an operation and holds it for the rest of the transaction.
     *
     * <pre>{@code
     * SELECT * FROM payment_operations WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> Applying a provider outcome locks the
     * operation and its obligation in that order, appends the observation, transitions once, and
     * commits before anything is told about it.</p>
     *
     * @param id operation to lock
     * @return the locked operation, when it exists
     */
    @Query("SELECT * FROM payment_operations WHERE id = :id FOR UPDATE")
    Optional<PaymentOperation> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Finds the operation behind one provider request key.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ? AND provider_request_key = ?}, matching
     * {@code uk_payment_operations_provider_key}. This is the read that turns a transport retry into a
     * continuation of the original request rather than a second charge.</p>
     *
     * @param providerAccountId merchant account the request went to
     * @param providerRequestKey key the provider deduplicates on
     * @return the operation, when one exists
     */
    Optional<PaymentOperation> findByProviderAccountIdAndProviderRequestKey(
            UUID providerAccountId, String providerRequestKey);

    /**
     * Finds the operation a provider reference belongs to.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ? AND provider_operation_ref = ?}. Used when
     * a webhook or a reconciliation row names a provider object and the platform has to decide whether
     * it already owns it.</p>
     *
     * @param providerAccountId merchant account the reference belongs to
     * @param providerOperationRef provider identifier
     * @return the operation, when one exists
     */
    Optional<PaymentOperation> findByProviderAccountIdAndProviderOperationRef(
            UUID providerAccountId, String providerOperationRef);

    /**
     * Finds the internal idempotency record for a scoped key.
     *
     * <p>Spring derives {@code WHERE idempotency_scope = ? AND idempotency_key = ?}.</p>
     *
     * @param idempotencyScope namespace the key is unique within
     * @param idempotencyKey key identifying the intent
     * @return the operation already created for that intent, when one exists
     */
    Optional<PaymentOperation> findByIdempotencyScopeAndIdempotencyKey(
            String idempotencyScope, String idempotencyKey);

    /**
     * Returns everything requested for one obligation, oldest first.
     *
     * <p>Spring derives {@code WHERE obligation_id = ? ORDER BY created_at}.</p>
     *
     * @param obligationId obligation whose operations are wanted
     * @return possibly empty list of operations
     */
    List<PaymentOperation> findAllByObligationIdOrderByCreatedAt(UUID obligationId);

    /**
     * Claims operations the executor should submit or retry.
     *
     * <pre>{@code
     * SELECT *
     * FROM payment_operations
     * WHERE state IN ('READY_TO_SUBMIT', 'PENDING', 'REQUIRES_ACTION')
     *   AND (next_attempt_at IS NULL OR next_attempt_at <= :decisionInstant)
     * ORDER BY next_attempt_at NULLS FIRST
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> A claimed operation moves to
     * {@code SUBMITTING} and takes the lease in a short transaction that commits before the network
     * call, so no lock is held across it.</p>
     *
     * @param decisionInstant the executor's single decision instant
     * @param batchSize maximum number of operations to claim
     * @return possibly empty list of claimed operations
     */
    @Query("""
            SELECT *
            FROM payment_operations
            WHERE state IN ('READY_TO_SUBMIT', 'PENDING', 'REQUIRES_ACTION')
              AND (next_attempt_at IS NULL OR next_attempt_at <= :decisionInstant)
            ORDER BY next_attempt_at NULLS FIRST
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<PaymentOperation> claimSubmittable(
            @Param("decisionInstant") Instant decisionInstant,
            @Param("batchSize") int batchSize);

    /**
     * Returns submitted operations whose outcome is still unproven and old enough to chase.
     *
     * <pre>{@code
     * SELECT *
     * FROM payment_operations
     * WHERE state IN ('UNKNOWN', 'PENDING', 'SUBMITTING')
     *   AND submitted_at <= :staleBefore
     * ORDER BY submitted_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>These are resolved by querying the provider, never by submitting a replacement. Money may
     * already have moved.</p>
     *
     * @param staleBefore operations submitted at or before this instant are considered stale
     * @param batchSize maximum number of operations to return
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM payment_operations
            WHERE state IN ('UNKNOWN', 'PENDING', 'SUBMITTING')
              AND submitted_at <= :staleBefore
            ORDER BY submitted_at
            LIMIT :batchSize
            """)
    List<PaymentOperation> findUnresolved(
            @Param("staleBefore") Instant staleBefore,
            @Param("batchSize") int batchSize);

    /**
     * Returns the successful captures an obligation can be refunded from, oldest first.
     *
     * <pre>{@code
     * SELECT *
     * FROM payment_operations
     * WHERE obligation_id = :obligationId
     *   AND state = 'SUCCEEDED'
     *   AND operation_type IN ('CAPTURE', 'SALE')
     * ORDER BY resolved_at
     * }</pre>
     *
     * <p>Ordered by when the money was actually taken, so a refund spanning several captures allocates
     * against them deterministically.</p>
     *
     * @param obligationId obligation whose captures are wanted
     * @return possibly empty list of captures, oldest first
     */
    @Query("""
            SELECT *
            FROM payment_operations
            WHERE obligation_id = :obligationId
              AND state = 'SUCCEEDED'
              AND operation_type IN ('CAPTURE', 'SALE')
            ORDER BY resolved_at
            """)
    List<PaymentOperation> findRefundableCaptures(@Param("obligationId") UUID obligationId);
}
