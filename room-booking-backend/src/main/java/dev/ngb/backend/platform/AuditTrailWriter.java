package dev.ngb.backend.platform;

/**
 * Port used by application code to append tamper-evident evidence of a privileged action.
 *
 * <p>An interface defines behavior without implementation, matching {@link EmailSender}: a module
 * depends on this abstraction, while Spring injects the concrete writer. Callers never construct or
 * save an {@code AuditEvent} directly — it lives in {@code platform.internal} — so this is the only
 * way outside the platform module to append a row.</p>
 */
public interface AuditTrailWriter {

    /**
     * Appends one row of evidence.
     *
     * @param entry the action, outcome, target, and actor to record
     */
    void record(AuditEntry entry);
}
