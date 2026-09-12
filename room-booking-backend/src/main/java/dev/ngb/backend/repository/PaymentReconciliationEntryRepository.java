package dev.ngb.backend.repository;

import dev.ngb.backend.model.PaymentReconciliationEntry;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Reads the append-only record of what was compared.
 *
 * <p>The table rejects updates and deletes by trigger. A revised comparison is a new entry in a
 * later run.</p>
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payment_reconciliation_entries}.</p>
 */
public interface PaymentReconciliationEntryRepository extends ListCrudRepository<PaymentReconciliationEntry, UUID> {

    /**
     * Returns one run's comparisons, worst first.
     *
     * <p>Spring derives {@code WHERE run_id = ? ORDER BY materiality DESC, compared_at}.</p>
     *
     * @param runId run whose comparisons are wanted
     * @return possibly empty list of entries
     */
    List<PaymentReconciliationEntry> findAllByRunIdOrderByMaterialityDescComparedAt(UUID runId);

    /**
     * Returns the differences a run found, worst and oldest first.
     *
     * <pre>{@code
     * SELECT *
     * FROM payment_reconciliation_entries
     * WHERE run_id = :runId
     *   AND outcome <> 'MATCHED'
     * ORDER BY materiality DESC, compared_at
     * }</pre>
     *
     * <p>Exceptions are aged by monetary materiality and booking impact, not by arrival order.</p>
     *
     * @param runId run whose exceptions are wanted
     * @return possibly empty list of entries
     */
    @Query("""
            SELECT *
            FROM payment_reconciliation_entries
            WHERE run_id = :runId
              AND outcome <> 'MATCHED'
            ORDER BY materiality DESC, compared_at
            """)
    List<PaymentReconciliationEntry> findExceptions(@Param("runId") UUID runId);

    /**
     * Returns every comparison ever made about one operation, oldest first.
     *
     * <p>Spring derives {@code WHERE operation_id = ? ORDER BY compared_at}. An operation compared
     * across several runs accumulates entries; reading them in order is how a changing verdict is
     * explained.</p>
     *
     * @param operationId operation whose comparisons are wanted
     * @return possibly empty list of entries
     */
    List<PaymentReconciliationEntry> findAllByOperationIdOrderByComparedAt(UUID operationId);
}
