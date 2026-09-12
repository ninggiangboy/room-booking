package dev.ngb.backend.repository;

import dev.ngb.backend.model.PayoutHold;
import dev.ngb.backend.model.PayoutHoldState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads the holds that stop a host being paid.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payout_holds}.</p>
 */
public interface PayoutHoldRepository extends ListCrudRepository<PayoutHold, UUID> {

    /**
     * Returns every hold currently stopping a host's money.
     *
     * <pre>{@code
     * SELECT *
     * FROM payout_holds
     * WHERE host_account_holder_id = :hostAccountHolderId
     *   AND state = 'ACTIVE'
     *   AND effective_from <= :at
     *   AND (expires_at IS NULL OR expires_at > :at)
     * ORDER BY effective_from
     * }</pre>
     *
     * <p>Matches {@code idx_payout_holds_scope}. Every hold is returned rather than a boolean, because a
     * payout that is blocked must be able to tell the host which reasons apply.</p>
     *
     * @param hostAccountHolderId host being evaluated
     * @param at instant the eligibility decision is being made at
     * @return possibly empty list of active holds, oldest first
     */
    @Query("""
            SELECT *
            FROM payout_holds
            WHERE host_account_holder_id = :hostAccountHolderId
              AND state = 'ACTIVE'
              AND effective_from <= :at
              AND (expires_at IS NULL OR expires_at > :at)
            ORDER BY effective_from
            """)
    List<PayoutHold> findActiveForHost(
            @Param("hostAccountHolderId") UUID hostAccountHolderId, @Param("at") Instant at);

    /**
     * Returns the active holds scoped to one allocation.
     *
     * <p>Spring derives {@code WHERE payable_allocation_id = ? AND state = ?}, matching
     * {@code idx_payout_holds_allocation}.</p>
     *
     * @param payableAllocationId allocation being evaluated
     * @param state lifecycle to filter on, normally {@code ACTIVE}
     * @return possibly empty list of holds
     */
    List<PayoutHold> findAllByPayableAllocationIdAndState(
            UUID payableAllocationId, PayoutHoldState state);

    /**
     * Finds active holds that have lapsed or are due for review.
     *
     * <pre>{@code
     * SELECT *
     * FROM payout_holds
     * WHERE state = 'ACTIVE'
     *   AND ((expires_at IS NOT NULL AND expires_at <= :at)
     *        OR (review_due_at IS NOT NULL AND review_due_at <= :at))
     * ORDER BY COALESCE(expires_at, review_due_at)
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_payout_holds_expiry} and {@code idx_payout_holds_review}. A hold that nobody
     * revisits becomes an indefinite withholding, which is exactly what the review deadline exists to
     * prevent.</p>
     *
     * @param at instant the sweep is running at
     * @param batchSize most rows to return
     * @return possibly empty list of holds needing attention
     */
    @Query("""
            SELECT *
            FROM payout_holds
            WHERE state = 'ACTIVE'
              AND ((expires_at IS NOT NULL AND expires_at <= :at)
                   OR (review_due_at IS NOT NULL AND review_due_at <= :at))
            ORDER BY COALESCE(expires_at, review_due_at)
            LIMIT :batchSize
            """)
    List<PayoutHold> findNeedingAttention(
            @Param("at") Instant at, @Param("batchSize") int batchSize);
}
