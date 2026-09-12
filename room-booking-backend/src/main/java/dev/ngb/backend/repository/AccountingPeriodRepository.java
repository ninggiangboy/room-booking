package dev.ngb.backend.repository;

import dev.ngb.backend.model.AccountingPeriod;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads and claims the periods a transaction posts into.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code accounting_periods}.</p>
 */
public interface AccountingPeriodRepository extends ListCrudRepository<AccountingPeriod, UUID> {

    /**
     * Finds a period by its finance-facing label.
     *
     * <p>Spring derives {@code WHERE accounting_book_id = ? AND period_label = ?}, matching
     * {@code uk_accounting_periods_label}.</p>
     *
     * @param accountingBookId book the period belongs to
     * @param periodLabel label such as {@code 2026-09}
     * @return the period, when one exists
     */
    Optional<AccountingPeriod> findByAccountingBookIdAndPeriodLabel(
            UUID accountingBookId, String periodLabel);

    /**
     * Finds the period an accounting date falls in.
     *
     * <pre>{@code
     * SELECT *
     * FROM accounting_periods
     * WHERE accounting_book_id = :accountingBookId
     *   AND period_range @> CAST(:accountingDate AS date)
     * }</pre>
     *
     * <p>An exclusion constraint guarantees at most one period per book can contain a date, so this
     * returns at most one row whatever its state. The caller decides what to do when it comes back hard
     * closed: post into the open period with prior-period attribution, or escalate.</p>
     *
     * @param accountingBookId book to look in
     * @param accountingDate date the entry posts to
     * @return the containing period, when one has been opened
     */
    @Query("""
            SELECT *
            FROM accounting_periods
            WHERE accounting_book_id = :accountingBookId
              AND period_range @> CAST(:accountingDate AS date)
            """)
    Optional<AccountingPeriod> findContaining(
            @Param("accountingBookId") UUID accountingBookId,
            @Param("accountingDate") LocalDate accountingDate);

    /**
     * Locks a period so a close cannot race a posting.
     *
     * <pre>{@code
     * SELECT *
     * FROM accounting_periods
     * WHERE id = :id
     * FOR UPDATE
     * }</pre>
     *
     * <p><strong>Must be called inside a transaction.</strong> The close worker and the posting service
     * contend here: one wins the period and the other follows the approved late-event policy rather
     * than both deciding they were first.</p>
     *
     * @param id period to lock
     * @return the locked period, when it exists
     */
    @Query("""
            SELECT *
            FROM accounting_periods
            WHERE id = :id
            FOR UPDATE
            """)
    Optional<AccountingPeriod> findByIdForUpdate(@Param("id") UUID id);
}
