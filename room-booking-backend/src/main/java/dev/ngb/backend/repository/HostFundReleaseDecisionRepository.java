package dev.ngb.backend.repository;

import dev.ngb.backend.model.HostFundReleaseDecision;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads the record of why a host's money was or was not released.
 *
 * <p>Append-only: the table has no update path, so there is no save-and-amend here. A newer evaluation
 * is a new row.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code host_fund_release_decisions}.</p>
 */
public interface HostFundReleaseDecisionRepository extends ListCrudRepository<HostFundReleaseDecision, UUID> {

    /**
     * Returns the decisions made about one allocation, newest first.
     *
     * <p>Spring derives {@code WHERE payable_allocation_id = ? ORDER BY evaluated_at DESC}, matching
     * {@code idx_host_fund_release_decisions_allocation}. The first row is the current answer; the rest
     * are how it got there.</p>
     *
     * @param payableAllocationId allocation whose history is wanted
     * @return possibly empty list of decisions, newest first
     */
    List<HostFundReleaseDecision> findAllByPayableAllocationIdOrderByEvaluatedAtDesc(
            UUID payableAllocationId);

    /**
     * Finds decisions whose review date has arrived.
     *
     * <pre>{@code
     * SELECT *
     * FROM host_fund_release_decisions
     * WHERE next_review_at IS NOT NULL
     *   AND next_review_at <= :dueAt
     * ORDER BY next_review_at
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_host_fund_release_decisions_review}. A deferred decision that is never
     * re-asked is money the host never gets, so the review date is a worker queue and not a note.</p>
     *
     * @param dueAt instant the worker is evaluating at
     * @param batchSize most rows to return
     * @return possibly empty list of decisions due for re-evaluation
     */
    @Query("""
            SELECT *
            FROM host_fund_release_decisions
            WHERE next_review_at IS NOT NULL
              AND next_review_at <= :dueAt
            ORDER BY next_review_at
            LIMIT :batchSize
            """)
    List<HostFundReleaseDecision> findDueForReview(
            @Param("dueAt") Instant dueAt, @Param("batchSize") int batchSize);
}
