package dev.ngb.backend.model;

/**
 * How much a reconciliation difference matters.
 *
 * <p>Exceptions are aged by monetary materiality and booking impact, not by arrival order.</p>
 */
public enum ExceptionSeverity {
    /** Immaterial; batched for routine review. */
    LOW,
    /** Worth a person looking within the working day. */
    MEDIUM,
    /** Material money or a blocked booking. */
    HIGH,
    /** Systemic exposure; pages an owner. */
    CRITICAL
}
