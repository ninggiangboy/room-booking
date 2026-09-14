package dev.ngb.backend.support.internal.repository.claim;

import dev.ngb.backend.support.internal.model.claim.ExternalClaimSubmission;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads what was handed to a provider, and when.
 *
 * <p>Frozen once sent: an information request produces the next version rather than an edit to the
 * one that already left.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code external_claim_submissions}.</p>
 */
public interface ExternalClaimSubmissionRepository extends ListCrudRepository<ExternalClaimSubmission, UUID> {

    /**
     * Lists the submissions on a claim, newest version first.
     *
     * @param externalClaimId claim
     * @return possibly empty list
     */
    List<ExternalClaimSubmission> findByExternalClaimIdOrderBySubmissionVersionDesc(
            UUID externalClaimId);

    /**
     * Finds the submission an idempotency key already made.
     *
     * @param externalClaimId claim
     * @param idempotencyKey key the provider deduplicates on
     * @return the submission, when that key already went out
     */
    Optional<ExternalClaimSubmission> findByExternalClaimIdAndIdempotencyKey(UUID externalClaimId,
            String idempotencyKey);

    /**
     * Claims submissions whose outcome is still open and due to be queried.
     *
     * <pre>{@code
     * SELECT *
     * FROM external_claim_submissions
     * WHERE outcome IN ('PENDING', 'UNKNOWN')
     *   AND next_query_at IS NOT NULL
     *   AND next_query_at <= :at
     * ORDER BY next_query_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p>Requires an active transaction. Querying is how a retry happens; the identity never changes.</p>
     *
     * @param at instant to treat as now
     * @param batchSize maximum submissions to claim
     * @return possibly empty list, longest due first
     */
    @Query("""
            SELECT *
            FROM external_claim_submissions
            WHERE outcome IN ('PENDING', 'UNKNOWN')
              AND next_query_at IS NOT NULL
              AND next_query_at <= :at
            ORDER BY next_query_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<ExternalClaimSubmission> claimDueForQuery(@Param("at") Instant at,
            @Param("batchSize") int batchSize);
}
