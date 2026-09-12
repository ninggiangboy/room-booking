package dev.ngb.backend.model;

/**
 * How far an upload has moved through quarantine.
 *
 * <p>Only {@code APPROVED} objects may be cited by a message; the database enforces it, so a
 * pending or rejected upload can never be shown as though it had passed.</p>
 */
public enum AttachmentScanState {
    /** Declared, not yet verified. */
    PENDING,
    /** Checksum, type and malware checks are running. */
    SCANNING,
    /** Verified and safe to attach. */
    APPROVED,
    /** Failed verification; the reason is recorded. */
    REJECTED,
    /** Held because it is dangerous or disputed. */
    QUARANTINED
}
