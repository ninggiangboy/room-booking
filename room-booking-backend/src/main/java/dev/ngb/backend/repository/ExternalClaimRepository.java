package dev.ngb.backend.repository;

import dev.ngb.backend.model.ExternalClaim;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the claims a provider holds on our behalf.
 *
 * <p>A lost response is answered by querying the stable provider key, never by opening a second
 * claim, so the key lookup is the recovery path rather than a convenience.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code external_claims}.</p>
 */
public interface ExternalClaimRepository extends ListCrudRepository<ExternalClaim, UUID> {

    /**
     * Finds the claim already open under a provider key.
     *
     * <pre>{@code
     * SELECT * FROM external_claims WHERE provider_account_id = :providerAccountId AND provider_claim_key = :providerClaimKey
     * }</pre>
     *
     * <p>Matches {@code uk_external_claims_provider_key}, so at most one row can come back.</p>
     *
     * @param providerAccountId provider account
     * @param providerClaimKey stable key written before the first call
     * @return the claim opened under that key, when there is one
     */
    @Query("SELECT * FROM external_claims WHERE provider_account_id = :providerAccountId AND provider_claim_key = :providerClaimKey")
    Optional<ExternalClaim> findByProviderKey(@Param("providerAccountId") UUID providerAccountId,
            @Param("providerClaimKey") String providerClaimKey);

    /**
     * Locks one claim before an observation is applied to it.
     *
     * <pre>{@code
     * SELECT * FROM external_claims WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction. The applied observation sequence is read and advanced under
     * this lock, which is what makes a late delivery detectably stale rather than silently winning.</p>
     *
     * @param id claim to lock
     * @return the locked claim, when it exists
     */
    @Query("SELECT * FROM external_claims WHERE id = :id FOR UPDATE")
    Optional<ExternalClaim> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Claims claims whose outcome is still unknown and due to be queried.
     *
     * <pre>{@code
     * SELECT *
     * FROM external_claims
     * WHERE state IN ('UNKNOWN', 'SUBMITTING', 'SUBMITTED', 'PROVIDER_REVIEW', 'PAYMENT_PENDING')
     *   AND next_query_at IS NOT NULL
     *   AND next_query_at <= :at
     * ORDER BY next_query_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum claims to claim
     * @return possibly empty list, longest due first
     */
    @Query("""
            SELECT *
            FROM external_claims
            WHERE state IN ('UNKNOWN', 'SUBMITTING', 'SUBMITTED', 'PROVIDER_REVIEW', 'PAYMENT_PENDING')
              AND next_query_at IS NOT NULL
              AND next_query_at <= :at
            ORDER BY next_query_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<ExternalClaim> claimDueForQuery(@Param("at") Instant at, @Param("batchSize") int batchSize);

    /**
     * Claims claims whose submission deadline is approaching.
     *
     * <pre>{@code
     * SELECT *
     * FROM external_claims
     * WHERE state IN ('LOCAL_APPROVED', 'SUBMISSION_QUEUED', 'INFO_REQUIRED')
     *   AND submission_deadline_at IS NOT NULL
     *   AND submission_deadline_at <= :at
     * ORDER BY submission_deadline_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum claims to claim
     * @return possibly empty list, nearest deadline first
     */
    @Query("""
            SELECT *
            FROM external_claims
            WHERE state IN ('LOCAL_APPROVED', 'SUBMISSION_QUEUED', 'INFO_REQUIRED')
              AND submission_deadline_at IS NOT NULL
              AND submission_deadline_at <= :at
            ORDER BY submission_deadline_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<ExternalClaim> claimApproachingDeadline(@Param("at") Instant at,
            @Param("batchSize") int batchSize);
}
