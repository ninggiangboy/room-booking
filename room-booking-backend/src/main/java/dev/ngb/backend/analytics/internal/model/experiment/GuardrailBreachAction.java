package dev.ngb.backend.analytics.internal.model.experiment;

/**
 * What happens when a guardrail is breached.
 */
public enum GuardrailBreachAction {

    /** Notify the owner. */
    ALERT,

    /** Suspend delivery automatically. */
    PAUSE,

    /** End the epoch automatically. */
    STOP
}
