package dev.ngb.backend.model;

/**
 * What the analysis concluded.
 *
 * <p>INVALID is available precisely so that a run whose integrity checks failed can say so rather
 * than be quietly left without a conclusion.</p>
 */
public enum AnalysisConclusion {

    /** Roll the treatment out. */
    SHIP,

    /** Do not roll it out. */
    DO_NOT_SHIP,

    /** Neither shipping nor abandoning is supported. */
    INCONCLUSIVE,

    /** Promising enough to redesign and rerun. */
    ITERATE,

    /** The analysis cannot be interpreted; usually an integrity failure. */
    INVALID
}
