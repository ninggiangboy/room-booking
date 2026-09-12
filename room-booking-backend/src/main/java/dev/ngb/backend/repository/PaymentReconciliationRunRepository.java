package dev.ngb.backend.repository;

import dev.ngb.backend.model.PaymentReconciliationRun;
import dev.ngb.backend.model.ReconciliationRunType;
import org.springframework.data.repository.ListCrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the record of provider reconciliation runs.
 *
 * <p>CRUD methods inherited from {@code ListCrudRepository} generate ID-based select,
 * insert/update, and delete SQL for {@code payment_reconciliation_runs}.</p>
 */
public interface PaymentReconciliationRunRepository extends ListCrudRepository<PaymentReconciliationRun, UUID> {

    /**
     * Finds the run already recorded for one period.
     *
     * <p>Spring derives
     * {@code WHERE provider_account_id = ? AND run_type = ? AND period_start = ? AND period_end = ?},
     * matching {@code uk_payment_reconciliation_runs_period}. Restarting an import resumes the same run
     * rather than producing a second set of comparisons for the same rows.</p>
     *
     * @param providerAccountId merchant account being reconciled
     * @param runType why the run was started
     * @param periodStart UTC instant the period begins
     * @param periodEnd UTC instant it ends
     * @return the run, when one was already recorded
     */
    Optional<PaymentReconciliationRun> findByProviderAccountIdAndRunTypeAndPeriodStartAndPeriodEnd(
            UUID providerAccountId, ReconciliationRunType runType,
            Instant periodStart, Instant periodEnd);

    /**
     * Returns a merchant account's runs, most recent period first.
     *
     * <p>Spring derives {@code WHERE provider_account_id = ? ORDER BY period_end DESC}.</p>
     *
     * @param providerAccountId merchant account whose runs are wanted
     * @return possibly empty list of runs
     */
    List<PaymentReconciliationRun> findAllByProviderAccountIdOrderByPeriodEndDesc(
            UUID providerAccountId);
}
