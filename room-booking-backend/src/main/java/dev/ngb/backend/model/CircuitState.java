package dev.ngb.backend.model;

/**
 * Health of a payment route, as the breaker sees it.
 *
 * <p>Open stops new submissions without touching in-flight operations: an outage must never change
 * who owns a request the provider already has.</p>
 */
public enum CircuitState {
    /** Healthy; new submissions flow. */
    CLOSED,
    /** Recovering; a limited number of submissions probe the provider. */
    HALF_OPEN,
    /** Failing; no new submissions, while webhooks, queries and refunds continue. */
    OPEN
}
