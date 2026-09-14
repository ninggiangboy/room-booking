package dev.ngb.backend.ml.internal.model.prediction;

/**
 * Whether a request produced a prediction, and if not, how it failed.
 *
 * <p>The seven failure states are recorded rather than omitted. Evaluating only the requests that
 * returned a score hides the outage and the fallback harm.</p>
 */
public enum PredictionStatus {

    /** The model answered. */
    PREDICTED,

    /** The inference budget ran out before an answer arrived. */
    TIMED_OUT,

    /** A required feature had no value. */
    FEATURE_MISSING,

    /** A required feature was older than its declared time to live. */
    FEATURE_STALE,

    /** No routed version could serve the request. */
    MODEL_UNAVAILABLE,

    /** The request fell outside the scope the version was approved for. */
    OUT_OF_SCOPE,

    /** The subject has not permitted this purpose. */
    CONSENT_DENIED,

    /** An invalidation blocks the model for this subject. */
    SUPPRESSED
}
