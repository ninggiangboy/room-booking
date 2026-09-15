package dev.ngb.backend.trust.internal.model.intervention;

/**
 * What happens to a protected action when evaluation cannot complete.
 *
 * <p>Registered in advance rather than decided by whichever timeout fires first. An urgent safety
 * action may not choose {@code FAIL_OPEN}.</p>
 */
public enum RiskFailureMode {
    /** The action is refused when no decision can be produced. */
    FAIL_CLOSED,
    /** The action proceeds under monitoring; permitted only for low-tier reversible actions. */
    FAIL_OPEN,
    /** A named fallback outcome applies, itself drawn from the permitted set. */
    REGISTERED_FALLBACK
}
