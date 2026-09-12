package dev.ngb.backend.repository;

import dev.ngb.backend.model.RelocationExpense;
import dev.ngb.backend.model.RelocationExpenseState;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads receipts from moving a guest.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code relocation_expenses}.</p>
 */
public interface RelocationExpenseRepository extends ListCrudRepository<RelocationExpense, UUID> {

    /**
     * Returns one case's expenses in the order they were claimed.
     *
     * <p>Spring derives {@code WHERE relocation_case_id = ? ORDER BY sequence_number}.</p>
     *
     * @param relocationCaseId case whose expenses are wanted
     * @return possibly empty list, first claim first
     */
    List<RelocationExpense> findAllByRelocationCaseIdOrderBySequenceNumber(UUID relocationCaseId);

    /**
     * Returns what a case has approved in expenses.
     *
     * <pre>{@code
     * SELECT COALESCE(sum(approved_amount_minor), 0)
     * FROM relocation_expenses
     * WHERE relocation_case_id = :relocationCaseId AND state IN ('APPROVED', 'REIMBURSED')
     * }</pre>
     *
     * <p>Read under the case lock before approving another claim, because the budget ceiling on the case
     * is checked against this total.</p>
     *
     * @param relocationCaseId case to total
     * @return approved minor units, zero when nothing has been approved
     */
    @Query("""
            SELECT COALESCE(sum(approved_amount_minor), 0)
            FROM relocation_expenses
            WHERE relocation_case_id = :relocationCaseId AND state IN ('APPROVED', 'REIMBURSED')
            """)
    long sumApproved(@Param("relocationCaseId") UUID relocationCaseId);

    /**
     * Returns claims still waiting for a decision, oldest first.
     *
     * <p>Spring derives {@code WHERE state = ? ORDER BY created_at}, covered by
     * {@code idx_relocation_expenses_pending}.</p>
     *
     * @param state state to filter on, normally {@code CLAIMED}
     * @return possibly empty list, oldest first
     */
    List<RelocationExpense> findAllByStateOrderByCreatedAt(RelocationExpenseState state);
}
