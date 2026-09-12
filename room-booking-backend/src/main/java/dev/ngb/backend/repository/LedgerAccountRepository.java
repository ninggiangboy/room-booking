package dev.ngb.backend.repository;

import dev.ngb.backend.model.LedgerAccount;
import dev.ngb.backend.model.LedgerAccountLifecycle;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the chart of accounts the posting engine resolves against.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code ledger_accounts}.</p>
 */
public interface LedgerAccountRepository extends ListCrudRepository<LedgerAccount, UUID> {

    /**
     * Finds one account by its code within a book.
     *
     * <p>Spring derives {@code WHERE accounting_book_id = ? AND account_code = ?}, matching
     * {@code uk_ledger_accounts_code}. This is the lookup a posting rule performs for every posting it
     * proposes.</p>
     *
     * @param accountingBookId book the account belongs to
     * @param accountCode stable account code
     * @return the account, when one exists
     */
    Optional<LedgerAccount> findByAccountingBookIdAndAccountCode(
            UUID accountingBookId, String accountCode);

    /**
     * Returns the accounts of a family that are open for posting.
     *
     * <p>Spring derives
     * {@code WHERE accounting_book_id = ? AND account_family = ? AND lifecycle_state = ?}, matching
     * {@code idx_ledger_accounts_family}.</p>
     *
     * @param accountingBookId book to look in
     * @param accountFamily finance grouping such as {@code HOST_PAYABLE}
     * @param lifecycleState lifecycle to filter on, normally {@code ACTIVE}
     * @return possibly empty list of accounts
     */
    List<LedgerAccount> findAllByAccountingBookIdAndAccountFamilyAndLifecycleState(
            UUID accountingBookId, String accountFamily, LedgerAccountLifecycle lifecycleState);
}
