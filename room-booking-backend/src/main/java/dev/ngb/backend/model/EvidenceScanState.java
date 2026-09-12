package dev.ngb.backend.model;

/**
 * Malware-scan result for one piece of dispute evidence.
 *
 * <p>A file nobody scanned, or one that came back infected, is never sent to a provider.</p>
 */
public enum EvidenceScanState {
    /** Not yet scanned. */
    PENDING,
    /** Scanned with no finding. */
    CLEAN,
    /** Scanned and found dangerous. */
    INFECTED,
    /** The scan could not complete. */
    FAILED,
    /** Structured evidence with no file to scan. */
    NOT_APPLICABLE
}
