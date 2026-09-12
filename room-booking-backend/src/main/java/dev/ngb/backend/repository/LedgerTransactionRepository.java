package dev.ngb.backend.repository;

import dev.ngb.backend.model.LedgerTransaction;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the journal.
 *
 * <p>Insert and update go through the inherited CRUD methods. Once a transaction is posted the database
 * refuses any change to it, so a correction is written as a reversal plus a new transaction rather
 * than as a save of an existing one.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code ledger_transactions}.</p>
 */
public interface LedgerTransactionRepository extends ListCrudRepository<LedgerTransaction, UUID> {

    /**
     * Finds the transaction a source fact already produced.
     *
     * <p>Spring derives
     * {@code WHERE accounting_book_id = ? AND source_type = ? AND source_id = ? AND posting_purpose = ?},
     * matching {@code uk_ledger_transactions_source}. This is the read that makes a replayed event, a
     * duplicated webhook, and a retried worker converge on the entry that already exists instead of
     * posting the money again.</p>
     *
     * @param accountingBookId book the entry would post into
     * @param sourceType kind of fact
     * @param sourceId identity of that fact
     * @param postingPurpose which accounting effect of it
     * @return the existing transaction, when the fact has already been posted
     */
    Optional<LedgerTransaction> findByAccountingBookIdAndSourceTypeAndSourceIdAndPostingPurpose(
            UUID accountingBookId, String sourceType, UUID sourceId, String postingPurpose);

    /**
     * Returns every entry produced by one source fact, across purposes.
     *
     * <p>Spring derives {@code WHERE source_type = ? AND source_id = ? ORDER BY created_at}, matching
     * {@code idx_ledger_transactions_source_lookup}. Support uses it to answer "what did this booking do
     * to the ledger".</p>
     *
     * @param sourceType kind of fact
     * @param sourceId identity of that fact
     * @return possibly empty list of transactions, oldest first
     */
    List<LedgerTransaction> findAllBySourceTypeAndSourceIdOrderByCreatedAt(
            String sourceType, UUID sourceId);

    /**
     * Claims source work that has not reached the journal yet.
     *
     * <pre>{@code
     * SELECT *
     * FROM ledger_transactions
     * WHERE state IN ('RECEIVED', 'VALIDATED')
     * ORDER BY created_at
     * LIMIT :batchSize
     * FOR UPDATE SKIP LOCKED
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> {@code SKIP LOCKED} lets several posting
     * workers run without contending, and {@code REVIEW_REQUIRED} is excluded because that work is
     * waiting for a person rather than for a worker.</p>
     *
     * @param batchSize most rows to claim
     * @return the claimed transactions, oldest first
     */
    @Query("""
            SELECT *
            FROM ledger_transactions
            WHERE state IN ('RECEIVED', 'VALIDATED')
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """)
    List<LedgerTransaction> claimUnposted(@Param("batchSize") int batchSize);

    /**
     * Finds the reversal of a transaction, when one has been posted.
     *
     * <pre>{@code
     * SELECT *
     * FROM ledger_transactions
     * WHERE reverses_transaction_id = :transactionId
     *   AND state = 'POSTED'
     * }</pre>
     *
     * <p>A partial unique index allows at most one posted reversal per transaction, so this returns at
     * most one row. A second reversal would negate the same effect twice.</p>
     *
     * @param transactionId transaction whose reversal is wanted
     * @return the reversal, when one exists
     */
    @Query("""
            SELECT *
            FROM ledger_transactions
            WHERE reverses_transaction_id = :transactionId
              AND state = 'POSTED'
            """)
    Optional<LedgerTransaction> findPostedReversalOf(@Param("transactionId") UUID transactionId);
}
