package dev.ngb.backend.support.internal.repository.remedy;

import dev.ngb.backend.support.internal.model.remedy.RemedyBudgetWindow;
import dev.ngb.backend.support.internal.model.remedy.BudgetScope;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.ngb.backend.support.internal.model.remedy.BudgetScope;
import dev.ngb.backend.support.internal.model.remedy.RemedyBudgetWindow;


/**
 * Reads and locks the goodwill budgets.
 *
 * <p>These limits bound abuse and mistake. They never reduce a contractual entitlement, because a
 * contractual line is not charged against a window at all.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code remedy_budget_windows}.</p>
 */
public interface RemedyBudgetWindowRepository extends ListCrudRepository<RemedyBudgetWindow, UUID> {

    /**
     * Locks the window for a scope and period.
     *
     * <pre>{@code
     * SELECT * FROM remedy_budget_windows
     * WHERE budget_scope = :budgetScope
     *   AND scope_key = :scopeKey
     *   AND currency = :currency
     *   AND window_from = :windowFrom
     * FOR UPDATE
     * }</pre>
     *
     * <p>Requires an active transaction. Matches {@code uk_remedy_budget_windows_identity}, so at most
     * one row can come back.</p>
     *
     * @param budgetScope what the budget is scoped to
     * @param scopeKey the thing it belongs to
     * @param currency currency of the limit
     * @param windowFrom start of the period
     * @return the locked window, when one exists
     */
    @Query("""
            SELECT * FROM remedy_budget_windows
            WHERE budget_scope = :budgetScope
              AND scope_key = :scopeKey
              AND currency = :currency
              AND window_from = :windowFrom
            FOR UPDATE
            """)
    Optional<RemedyBudgetWindow> findForUpdate(@Param("budgetScope") String budgetScope,
            @Param("scopeKey") String scopeKey, @Param("currency") String currency,
            @Param("windowFrom") Instant windowFrom);

    /**
     * Lists the open windows for one scope.
     *
     * @param budgetScope what the budget is scoped to
     * @param scopeKey the thing it belongs to
     * @return possibly empty list
     */
    List<RemedyBudgetWindow> findByBudgetScopeAndScopeKey(BudgetScope budgetScope, String scopeKey);
}
