package dev.ngb.backend.repository;

import dev.ngb.backend.model.FinanceReconciliationRun;
import dev.ngb.backend.model.ReconciliationControlLayer;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the controls that compare the ledger with the outside world.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code finance_reconciliation_runs}.</p>
 */
public interface FinanceReconciliationRunRepository extends ListCrudRepository<FinanceReconciliationRun, UUID> {

    /**
     * Returns the most recent runs of one control layer.
     *
     * <p>Spring derives {@code WHERE control_layer = ? ORDER BY coverage_end DESC}, matching
     * {@code idx_finance_reconciliation_runs_layer}. Each layer is monitored separately, because one
     * green total at one layer proves nothing about another.</p>
     *
     * @param controlLayer which pair of facts the runs compare
     * @return possibly empty list of runs, most recent coverage first
     */
    List<FinanceReconciliationRun> findAllByControlLayerOrderByCoverageEndDesc(
            ReconciliationControlLayer controlLayer);

    /**
     * Returns the runs that read one artifact.
     *
     * <p>Spring derives {@code WHERE artifact_id = ?}, matching
     * {@code idx_finance_reconciliation_runs_artifact}.</p>
     *
     * @param artifactId evidence the runs read
     * @return possibly empty list of runs
     */
    List<FinanceReconciliationRun> findAllByArtifactId(UUID artifactId);

    /**
     * Finds the latest completed run of a layer for an account and currency.
     *
     * <pre>{@code
     * SELECT *
     * FROM finance_reconciliation_runs
     * WHERE control_layer = :controlLayer
     *   AND legal_entity_id = :legalEntityId
     *   AND currency = :currency
     *   AND state = 'COMPLETED'
     * ORDER BY coverage_end DESC
     * LIMIT 1
     * }</pre>
     *
     * <p>Gives the next run its starting watermark, so coverage is continuous and a period is neither
     * skipped nor reconciled twice.</p>
     *
     * @param controlLayer which pair of facts to compare
     * @param legalEntityId entity being reconciled
     * @param currency ISO 4217 code
     * @return the last successful run, when there has been one
     */
    @Query("""
            SELECT *
            FROM finance_reconciliation_runs
            WHERE control_layer = :controlLayer
              AND legal_entity_id = :legalEntityId
              AND currency = :currency
              AND state = 'COMPLETED'
            ORDER BY coverage_end DESC
            LIMIT 1
            """)
    Optional<FinanceReconciliationRun> findLastCompleted(
            @Param("controlLayer") String controlLayer,
            @Param("legalEntityId") UUID legalEntityId,
            @Param("currency") String currency);
}
