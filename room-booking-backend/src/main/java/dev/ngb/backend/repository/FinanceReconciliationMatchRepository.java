package dev.ngb.backend.repository;

import dev.ngb.backend.model.FinanceReconciliationMatch;
import dev.ngb.backend.model.ReconciliationMatchOutcome;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads the verdicts a reconciliation run produced.
 *
 * <p>Append-only. A better answer supersedes an earlier one rather than overwriting it, so the history
 * of what the control believed is preserved.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code finance_reconciliation_matches}.</p>
 */
public interface FinanceReconciliationMatchRepository extends ListCrudRepository<FinanceReconciliationMatch, UUID> {

    /**
     * Returns a run's verdicts of one kind.
     *
     * <p>Spring derives {@code WHERE run_id = ? AND outcome = ?}, matching
     * {@code idx_finance_reconciliation_matches_run}.</p>
     *
     * @param runId run whose verdicts are wanted
     * @param outcome verdict to filter on
     * @return possibly empty list of matches
     */
    List<FinanceReconciliationMatch> findAllByRunIdAndOutcome(
            UUID runId, ReconciliationMatchOutcome outcome);

    /**
     * Returns every verdict recorded about one external row.
     *
     * <p>Spring derives {@code WHERE external_record_id = ? ORDER BY matched_at}, matching
     * {@code idx_finance_reconciliation_matches_external}.</p>
     *
     * @param externalRecordId external row whose history is wanted
     * @return possibly empty list of matches, oldest first
     */
    List<FinanceReconciliationMatch> findAllByExternalRecordIdOrderByMatchedAt(UUID externalRecordId);

    /**
     * Returns unresolved differences by how much they matter.
     *
     * <pre>{@code
     * SELECT *
     * FROM finance_reconciliation_matches
     * WHERE outcome NOT IN ('MATCHED_EXACT', 'MATCHED_AGGREGATE', 'EXPECTED_TIMING_DIFFERENCE')
     *   AND case_id IS NULL
     * ORDER BY materiality DESC, matched_at DESC
     * LIMIT :batchSize
     * }</pre>
     *
     * <p>Matches {@code idx_finance_reconciliation_matches_exceptions}. An expected timing difference is
     * excluded because it is a known effect rather than a problem; a difference with no case is one
     * nobody owns yet.</p>
     *
     * @param batchSize most rows to return
     * @return possibly empty list of unowned exceptions, most material first
     */
    @Query("""
            SELECT *
            FROM finance_reconciliation_matches
            WHERE outcome NOT IN ('MATCHED_EXACT', 'MATCHED_AGGREGATE', 'EXPECTED_TIMING_DIFFERENCE')
              AND case_id IS NULL
            ORDER BY materiality DESC, matched_at DESC
            LIMIT :batchSize
            """)
    List<FinanceReconciliationMatch> findUnownedExceptions(@Param("batchSize") int batchSize);
}
