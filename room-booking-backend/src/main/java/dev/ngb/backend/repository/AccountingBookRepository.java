package dev.ngb.backend.repository;

import dev.ngb.backend.model.AccountingBook;
import dev.ngb.backend.model.ConfigurationLifecycle;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the books the journal posts into.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code accounting_books}.</p>
 */
public interface AccountingBookRepository extends ListCrudRepository<AccountingBook, UUID> {

    /**
     * Finds a book by the key finance refers to it by.
     *
     * <p>Spring derives {@code WHERE book_key = ?}, matching {@code uk_accounting_books_key}.</p>
     *
     * @param bookKey stable identifier of the book
     * @return the book, when one exists
     */
    Optional<AccountingBook> findByBookKey(String bookKey);

    /**
     * Returns the books an entity keeps that are open for posting.
     *
     * <p>Spring derives {@code WHERE legal_entity_id = ? AND lifecycle_state = ?}.</p>
     *
     * @param legalEntityId entity whose books are wanted
     * @param lifecycleState lifecycle to filter on, normally {@code ACTIVE}
     * @return possibly empty list of books
     */
    List<AccountingBook> findAllByLegalEntityIdAndLifecycleState(
            UUID legalEntityId, ConfigurationLifecycle lifecycleState);
}
