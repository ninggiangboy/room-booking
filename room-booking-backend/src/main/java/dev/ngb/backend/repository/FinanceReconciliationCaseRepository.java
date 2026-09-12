package dev.ngb.backend.repository;

import dev.ngb.backend.model.FinanceReconciliationCase;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the differences somebody owns until they are explained.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code finance_reconciliation_cases}.</p>
 */
public interface FinanceReconciliationCaseRepository extends ListCrudRepository<FinanceReconciliationCase, UUID> {

    /**
     * Finds a case by the identifier finance and support both quote.
     *
     * <p>Spring derives {@code WHERE public_id = ?}, matching
     * {@code uk_finance_reconciliation_cases_public_id}.</p>
     *
     * @param publicId short public identifier
     * @return the case, when one exists
     */
    Optional<FinanceReconciliationCase> findByPublicId(String publicId);

    /**
     * Returns open cases by urgency and due date.
     *
     * <pre>{@code
     * SELECT *
     * FROM finance_reconciliation_cases
     * WHERE state <> 'RESOLVED'
     * ORDER BY severity DESC, due_at NULLS LAST
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_finance_reconciliation_cases_open}. Reopened cases appear here again, which
     * is the point of reopening one.</p>
     *
     * @param batchSize most rows to return
     * @return possibly empty list of open cases
     */
    @Query("""
            SELECT *
            FROM finance_reconciliation_cases
            WHERE state <> 'RESOLVED'
            ORDER BY severity DESC, due_at NULLS LAST
            LIMIT :batchSize
            """)
    List<FinanceReconciliationCase> findOpen(@Param("batchSize") int batchSize);

    /**
     * Returns the unresolved cases currently stopping a host being paid.
     *
     * <pre>{@code
     * SELECT *
     * FROM finance_reconciliation_cases
     * WHERE host_account_holder_id = :hostAccountHolderId
     *   AND blocks_payout = true
     *   AND state <> 'RESOLVED'
     * }</pre>
     *
     * <p>Matches {@code idx_finance_reconciliation_cases_payout_block}. The payout planner consults this
     * so that a host whose money is caught in an unexplained difference is told so, rather than simply
     * finding their balance smaller than they expected.</p>
     *
     * @param hostAccountHolderId host being evaluated
     * @return possibly empty list of blocking cases
     */
    @Query("""
            SELECT *
            FROM finance_reconciliation_cases
            WHERE host_account_holder_id = :hostAccountHolderId
              AND blocks_payout = true
              AND state <> 'RESOLVED'
            """)
    List<FinanceReconciliationCase> findBlockingPayout(
            @Param("hostAccountHolderId") UUID hostAccountHolderId);

    /**
     * Returns the unresolved cases currently stopping a period closing.
     *
     * <pre>{@code
     * SELECT *
     * FROM finance_reconciliation_cases
     * WHERE accounting_book_id = :accountingBookId
     *   AND blocks_period_close = true
     *   AND state <> 'RESOLVED'
     * }</pre>
     *
     * <p>Matches {@code idx_finance_reconciliation_cases_close_block}. A close that ignores these is a
     * close that cannot be reproduced later.</p>
     *
     * @param accountingBookId book being closed
     * @return possibly empty list of blocking cases
     */
    @Query("""
            SELECT *
            FROM finance_reconciliation_cases
            WHERE accounting_book_id = :accountingBookId
              AND blocks_period_close = true
              AND state <> 'RESOLVED'
            """)
    List<FinanceReconciliationCase> findBlockingClose(
            @Param("accountingBookId") UUID accountingBookId);
}
