package dev.ngb.backend.repository;

import dev.ngb.backend.model.AccessOperation;
import dev.ngb.backend.model.AccessOperationType;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the calls made to access providers.
 *
 * <p>The stable-key lookup is what makes a retry the same operation rather than a second one, which
 * matters most for revocation.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code access_operations}.</p>
 */
public interface AccessOperationRepository extends ListCrudRepository<AccessOperation, UUID> {

    /**
     * Finds an operation by its stable key.
     *
     * <p>Spring derives {@code WHERE access_grant_id = ? AND operation_type = ? AND operation_key = ?},
     * matching {@code uk_access_operations_key}.</p>
     *
     * @param accessGrantId grant
     * @param operationType what was asked of the provider
     * @param operationKey stable key for that ask
     * @return the existing operation, when the ask was already recorded
     */
    Optional<AccessOperation> findByAccessGrantIdAndOperationTypeAndOperationKey(UUID accessGrantId,
            AccessOperationType operationType, String operationKey);

    /**
     * Finds the operation a provider reference belongs to.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ? AND provider_reference = ?}, matching
     * {@code uk_access_operations_provider_reference}. Used when reconciling provider reports.</p>
     *
     * @param providerAccountId provider account
     * @param providerReference provider-native identifier
     * @return the operation, when the reference is known
     */
    Optional<AccessOperation> findByProviderAccountIdAndProviderReference(UUID providerAccountId,
            String providerReference);

    /**
     * Claims operations that are due to be submitted or retried.
     *
     * <pre>{@code
     * SELECT *
     * FROM access_operations
     * WHERE state IN ('PLANNED', 'FAILED', 'UNKNOWN')
     *   AND (next_retry_at IS NULL OR next_retry_at <= :at)
     * ORDER BY created_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. {@code UNKNOWN} is included on purpose: an unresolved outcome
     * is queried rather than assumed.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum operations to claim
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT *
            FROM access_operations
            WHERE state IN ('PLANNED', 'FAILED', 'UNKNOWN')
              AND (next_retry_at IS NULL OR next_retry_at <= :at)
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<AccessOperation> claimDue(@Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Finds operations whose worker lease has lapsed.
     *
     * <pre>{@code
     * SELECT *
     * FROM access_operations
     * WHERE state = 'SUBMITTING'
     *   AND lease_expires_at <= :at
     * ORDER BY lease_expires_at
     * }</pre>
     *
     * <p>A lapsed lease on a submitting operation is not a failure; the outcome is unknown and must be
     * queried before anything is retried.</p>
     *
     * @param at instant to treat as now
     * @return possibly empty list, longest lapsed first
     */
    @Query("""
            SELECT *
            FROM access_operations
            WHERE state = 'SUBMITTING'
              AND lease_expires_at <= :at
            ORDER BY lease_expires_at
            """)
    List<AccessOperation> findExpiredLeases(@Param("at") Instant at);
}
