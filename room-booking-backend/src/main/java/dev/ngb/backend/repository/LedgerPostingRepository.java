package dev.ngb.backend.repository;

import dev.ngb.backend.model.LedgerPosting;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reads the individual lines of the journal.
 *
 * <p>Balances are derived from these rows. They are never reconstructed from a booking status or a
 * provider dashboard, which is the whole reason the journal exists.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code ledger_postings}.</p>
 */
public interface LedgerPostingRepository extends ListCrudRepository<LedgerPosting, UUID> {

    /**
     * Returns the postings of one transaction in order.
     *
     * <p>Spring derives {@code WHERE transaction_id = ? ORDER BY sequence_number}, matching
     * {@code idx_ledger_postings_transaction}.</p>
     *
     * @param transactionId transaction whose postings are wanted
     * @return possibly empty list of postings, in sequence
     */
    List<LedgerPosting> findAllByTransactionIdOrderBySequenceNumber(UUID transactionId);

    /**
     * Sums an account's posted balance up to an instant.
     *
     * <pre>{@code
     * SELECT COALESCE(sum(CASE WHEN p.direction = 'DEBIT'
     *                          THEN p.amount_minor ELSE -p.amount_minor END), 0)
     * FROM ledger_postings p
     * JOIN ledger_transactions t ON t.id = p.transaction_id
     * WHERE p.ledger_account_id = :ledgerAccountId
     *   AND t.state = 'POSTED'
     *   AND t.posted_at <= :asOf
     * }</pre>
     *
     * <p>Signed in the database rather than in Java, so a large account does not have to be paged into
     * memory to be totalled. The result is a debit-positive balance; the caller reads it against the
     * account's normal balance.</p>
     *
     * @param ledgerAccountId account to total
     * @param asOf instant to total up to
     * @return debit-positive balance in minor units, zero when nothing has posted
     */
    @Query("""
            SELECT COALESCE(sum(CASE WHEN p.direction = 'DEBIT'
                                     THEN p.amount_minor ELSE -p.amount_minor END), 0)
            FROM ledger_postings p
            JOIN ledger_transactions t ON t.id = p.transaction_id
            WHERE p.ledger_account_id = :ledgerAccountId
              AND t.state = 'POSTED'
              AND t.posted_at <= :asOf
            """)
    long balanceAsOf(@Param("ledgerAccountId") UUID ledgerAccountId, @Param("asOf") Instant asOf);

    /**
     * Returns a host's posted entries in one currency, most recent first.
     *
     * <pre>{@code
     * SELECT p.*
     * FROM ledger_postings p
     * JOIN ledger_transactions t ON t.id = p.transaction_id
     * WHERE p.host_account_holder_id = :hostAccountHolderId
     *   AND p.currency = :currency
     *   AND t.state = 'POSTED'
     * ORDER BY p.created_at DESC
     * LIMIT :limit
     * }</pre>
     *
     * <p>Matches {@code idx_ledger_postings_host}. This is what a host statement composes from.</p>
     *
     * @param hostAccountHolderId host whose entries are wanted
     * @param currency ISO 4217 code to restrict to
     * @param limit most rows to return
     * @return possibly empty list of postings
     */
    @Query("""
            SELECT p.*
            FROM ledger_postings p
            JOIN ledger_transactions t ON t.id = p.transaction_id
            WHERE p.host_account_holder_id = :hostAccountHolderId
              AND p.currency = :currency
              AND t.state = 'POSTED'
            ORDER BY p.created_at DESC
            LIMIT :limit
            """)
    List<LedgerPosting> findHostPostings(
            @Param("hostAccountHolderId") UUID hostAccountHolderId,
            @Param("currency") String currency,
            @Param("limit") int limit);

    /**
     * Returns every posting that names a booking.
     *
     * <p>Spring derives {@code WHERE booking_id = ? ORDER BY created_at}, matching
     * {@code idx_ledger_postings_booking}.</p>
     *
     * @param bookingId booking whose accounting effects are wanted
     * @return possibly empty list of postings, oldest first
     */
    List<LedgerPosting> findAllByBookingIdOrderByCreatedAt(UUID bookingId);
}
