package dev.ngb.backend.trust.internal.model.decision;

/**
 * Which registered fallback produced a decision.
 *
 * <p>Required whenever an action was allowed while a mandatory fact was missing, so an outage can
 * never be read afterwards as a clean approval.</p>
 */
public enum RiskFallbackMode {
    /** The fallback outcome registered for this action. */
    REGISTERED_FALLBACK,
    /** Deterministic controls decided alone. */
    DETERMINISTIC_ONLY,
    /** Model contribution was switched off. */
    MODEL_DISABLED,
    /** A provider on the critical path did not answer. */
    PROVIDER_TIMEOUT;
}
