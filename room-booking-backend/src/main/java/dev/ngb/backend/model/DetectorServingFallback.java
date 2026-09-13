package dev.ngb.backend.model;

/**
 * Why a detector produced no finding.
 *
 * <p>A detector that did not run has no category to offer, paired by check constraint: recording one
 * anyway is how an outage becomes a clean bill of health.</p>
 */
public enum DetectorServingFallback {
    /** The detector ran normally. */
    NONE,
    /** It did not answer in time. */
    TIMEOUT,
    /** It was disabled. */
    KILL_SWITCH,
    /** It could not be reached. */
    UNAVAILABLE,
    /** It does not cover this language. */
    UNSUPPORTED_LANGUAGE;
}
