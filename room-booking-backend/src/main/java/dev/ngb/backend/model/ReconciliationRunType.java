package dev.ngb.backend.model;

/**
 * Why a reconciliation run was started.
 *
 * <p>The absence of a webhook is not proof of absence at the provider, which is why recovery runs
 * exist alongside scheduled imports.</p>
 */
public enum ReconciliationRunType {
    /** Near-real-time sweep of operations with no terminal provider fact. */
    STATUS_RECOVERY,
    /** Scheduled completeness check against a provider export. */
    DAILY_IMPORT,
    /** Aligned to the provider's own settlement period. */
    SETTLEMENT_CYCLE,
    /** Started by an operator, typically during an incident. */
    MANUAL
}
