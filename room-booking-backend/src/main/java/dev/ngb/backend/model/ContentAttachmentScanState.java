package dev.ngb.backend.model;

/**
 * What the malware and type validation found.
 *
 * <p>Files are validated by their bytes rather than the type they declare, scanned in isolated
 * infrastructure and never executed in a reviewer browser.</p>
 */
public enum ContentAttachmentScanState {
    /** Not yet scanned. */
    PENDING,
    /** Scanned and nothing found. */
    CLEAN,
    /** Malware found. */
    INFECTED,
    /** The file type cannot be validated. */
    UNSUPPORTED,
    /** The scan could not complete. */
    FAILED;
}
