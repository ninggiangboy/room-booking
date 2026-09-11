package dev.ngb.backend.model;

/**
 * Result of an audited action.
 *
 * <p>Denied and failed attempts are audited as deliberately as successful ones: an audit trail that
 * recorded only successes could not show that an attacker tried and was stopped.</p>
 */
public enum AuditOutcome {
    /** The action was authorized and applied. */
    ALLOWED,
    /** The action was refused by authorization or policy. */
    DENIED,
    /** The action was authorized but did not complete. */
    FAILED,
    /** Some of the intended effect was applied. */
    PARTIAL
}
