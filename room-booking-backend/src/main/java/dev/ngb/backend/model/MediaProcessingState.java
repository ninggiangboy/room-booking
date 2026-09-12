package dev.ngb.backend.model;

/**
 * Whether an uploaded file has been transformed into its delivery renditions.
 *
 * <p>Tracked separately from scanning and moderation because the three fail independently: a file
 * can be malware-free and permitted but not yet resized, and serving an unprocessed original is both
 * slow and a way to leak camera metadata.</p>
 */
public enum MediaProcessingState {
    /** Uploaded, not yet processed. */
    PENDING,
    /** Renditions are being produced. */
    PROCESSING,
    /** Renditions exist and may be served. */
    READY,
    /** Processing failed; the file must not be served. */
    FAILED
}
