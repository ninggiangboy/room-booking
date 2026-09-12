package dev.ngb.backend.model;

/**
 * Result of scanning an uploaded document for malware.
 *
 * <p>Nothing may read a document until it is {@link #CLEAN}. Uploaded files are attacker-controlled
 * input, and a reviewer opening one is exactly the path a malicious upload is aiming for.</p>
 */
public enum DocumentScanState {
    /** Not yet scanned; must not be opened. */
    PENDING,
    /** Scanned and safe to read. */
    CLEAN,
    /** Malware detected; quarantined. */
    INFECTED,
    /** The scan itself failed; treated as unsafe. */
    FAILED
}
