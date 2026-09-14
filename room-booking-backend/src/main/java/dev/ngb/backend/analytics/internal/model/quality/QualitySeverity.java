package dev.ngb.backend.analytics.internal.model.quality;

/**
 * How serious a failing check is.
 *
 * <p>A privacy check is always fatal: it exists to find data that should not be in the product at
 * all.</p>
 */
public enum QualitySeverity {

    /** Worth knowing. */
    WARN,

    /** Blocks the run from reporting itself clean. */
    FAIL
}
