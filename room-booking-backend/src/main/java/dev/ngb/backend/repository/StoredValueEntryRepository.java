package dev.ngb.backend.repository;

import dev.ngb.backend.model.StoredValueEntry;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the movements of a guest's stored-value balance.
 *
 * <p>Rows are append-only and every movement of the balance itself names the ledger transaction
 * that posted it, so this table is one side of the reconciliation against the ledger.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code stored_value_entries}.</p>
 */
public interface StoredValueEntryRepository extends ListCrudRepository<StoredValueEntry, UUID> {

    /**
     * Lists the movements on one balance, newest first.
     *
     * @param storedValueAccountId the balance
     * @return possibly empty list, most recent first
     */
    List<StoredValueEntry> findByStoredValueAccountIdOrderByOccurredAtDesc(
            UUID storedValueAccountId);

    /**
     * Lists the movements that drew on one lot.
     *
     * @param storedValueLotId the lot
     * @return possibly empty list
     */
    List<StoredValueEntry> findByStoredValueLotId(UUID storedValueLotId);

    /**
     * Finds the movement that reverses another, which is how a correction is followed forward.
     *
     * <pre>{@code
     * SELECT * FROM stored_value_entries WHERE reverses_entry_id = :reversesEntryId
     * }</pre>
     *
     * @param reversesEntryId the movement that was undone
     * @return the reversing movement, when there is one
     */
    @Query("SELECT * FROM stored_value_entries WHERE reverses_entry_id = :reversesEntryId")
    Optional<StoredValueEntry> findReversalOf(@Param("reversesEntryId") UUID reversesEntryId);

    /**
     * Lists the movements posted against one ledger transaction, which is what a finance query
     * starting from the ledger side follows back into this domain.
     *
     * <pre>{@code
     * SELECT * FROM stored_value_entries
     * WHERE ledger_transaction_id = :ledgerTransactionId
     * ORDER BY occurred_at
     * }</pre>
     *
     * @param ledgerTransactionId the posting
     * @return possibly empty list, oldest first
     */
    @Query("""
            SELECT * FROM stored_value_entries
            WHERE ledger_transaction_id = :ledgerTransactionId
            ORDER BY occurred_at
            """)
    List<StoredValueEntry> findByPosting(
            @Param("ledgerTransactionId") UUID ledgerTransactionId);
}
