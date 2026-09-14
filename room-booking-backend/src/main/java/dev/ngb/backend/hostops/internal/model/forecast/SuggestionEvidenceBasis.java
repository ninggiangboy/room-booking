package dev.ngb.backend.hostops.internal.model.forecast;

/**
 * What a promotion suggestion rests on. A model-based suggestion names the model and the run it
 * read, because "the system suggested it" is not something a host can ask a second question about.
 */
public enum SuggestionEvidenceBasis {

    /** Forecast model. */
    FORECAST_MODEL,

    /** Market benchmark. */
    MARKET_BENCHMARK,

    /** Historical uplift. */
    HISTORICAL_UPLIFT,

    /** Experiment result. */
    EXPERIMENT_RESULT,

    /** Heuristic. */
    HEURISTIC
}
