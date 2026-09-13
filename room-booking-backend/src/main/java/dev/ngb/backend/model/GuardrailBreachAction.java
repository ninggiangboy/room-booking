package dev.ngb.backend.model;

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
