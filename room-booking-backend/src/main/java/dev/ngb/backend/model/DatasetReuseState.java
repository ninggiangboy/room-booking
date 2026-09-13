package dev.ngb.backend.model;

/**
 * Whether a built dataset may still be used for a new release.
 *
 * <p>Invalidation never edits the artifact. A model already released against the dataset has to
 * stay reproducible even once the dataset may not be reused.</p>
 */
public enum DatasetReuseState {

    /** May be used for a new release. */
    REUSABLE,

    /** An erasure landed inside the window the build covered. */
    INVALIDATED_BY_DELETION,

    /** A correction changed data the build read. */
    INVALIDATED_BY_CORRECTION,

    /** Past its declared retention. */
    EXPIRED
}
