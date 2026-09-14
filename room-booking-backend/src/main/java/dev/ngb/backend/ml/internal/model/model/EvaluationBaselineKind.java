package dev.ngb.backend.ml.internal.model.model;

/**
 * What a model version was compared against.
 *
 * <p>The no-prediction and stale-feature baselines matter as much as the champion: a serving path
 * spends a measurable share of its traffic in those states.</p>
 */
public enum EvaluationBaselineKind {

    /** The deterministic behaviour already running. */
    PRODUCTION_HEURISTIC,

    /** A simple interpretable model, where one is suitable. */
    STATISTICAL_BASELINE,

    /** The model version currently serving this scope. */
    CURRENT_CHAMPION,

    /** What happens when nothing is scored at all. */
    NO_PREDICTION,

    /** What happens when the features are too old to use. */
    STALE_FEATURE_FALLBACK
}
