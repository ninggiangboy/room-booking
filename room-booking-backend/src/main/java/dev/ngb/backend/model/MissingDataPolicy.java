package dev.ngb.backend.model;

/**
 * What the analysis does about units with no outcome.
 *
 * <p>Declared before launch, because choosing it after seeing the data chooses the answer.</p>
 */
public enum MissingDataPolicy {

    /** Units with no outcome are dropped. */
    COMPLETE_CASE,

    /** Missing outcomes are filled by a rule stated in advance. */
    IMPUTE_DECLARED,

    /** Units whose outcome has not matured are treated as censored. */
    CENSOR,

    /** Units are removed from the analysis population entirely. */
    EXCLUDE_UNIT
}
