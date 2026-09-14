package dev.ngb.backend.repository;

import dev.ngb.backend.model.StoredValueAccount;
import dev.ngb.backend.model.StoredValueAccountKind;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads guest stored-value balances and the ledger liability behind each of them.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code stored_value_accounts}.</p>
 */
public interface StoredValueAccountRepository extends ListCrudRepository<StoredValueAccount, UUID> {

    /**
     * Finds one guest's balance of one kind in one currency.
     *
     * @param accountHolderId the guest
     * @param accountKind what kind of value
     * @param currency ISO 4217 alphabetic code
     * @return the balance, when it exists
     */
    Optional<StoredValueAccount> findByAccountHolderIdAndAccountKindAndCurrency(
            UUID accountHolderId, StoredValueAccountKind accountKind, String currency);

    /**
     * Lists every balance one guest holds.
     *
     * @param accountHolderId the guest
     * @return possibly empty list
     */
    List<StoredValueAccount> findByAccountHolderId(UUID accountHolderId);

    /**
     * Reads one balance under a row lock, which every movement of stored value must take before
     * writing its entry: the entry records the balance it produced, so two concurrent redemptions
     * must not compute it from the same starting point.
     *
     * <pre>{@code
     * SELECT * FROM stored_value_accounts WHERE id = :id FOR UPDATE
     * }</pre>
     *
     * <p>Requires an open transaction.</p>
     *
     * @param id the balance
     * @return the locked balance, when it exists
     */
    @Query("SELECT * FROM stored_value_accounts WHERE id = :id FOR UPDATE")
    Optional<StoredValueAccount> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Sums the outstanding liability carried on one ledger account, which is what a close
     * reconciles the ledger balance against.
     *
     * <pre>{@code
     * SELECT coalesce(sum(balance_minor), 0) FROM stored_value_accounts
     * WHERE liability_account_id = :liabilityAccountId AND lifecycle_state <> 'CLOSED'
     * }</pre>
     *
     * @param liabilityAccountId the ledger liability account
     * @return total outstanding, in integer minor units
     */
    @Query("""
            SELECT coalesce(sum(balance_minor), 0) FROM stored_value_accounts
            WHERE liability_account_id = :liabilityAccountId AND lifecycle_state <> 'CLOSED'
            """)
    long sumOutstanding(@Param("liabilityAccountId") UUID liabilityAccountId);
}
