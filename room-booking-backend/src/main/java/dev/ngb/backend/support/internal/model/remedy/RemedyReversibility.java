package dev.ngb.backend.support.internal.model.remedy;

/**
 * The reversibility of {@code remedy_catalog_versions}.
 */
public enum RemedyReversibility {

    /** Irreversible. */
    IRREVERSIBLE,

    /** Reversible by decision. */
    REVERSIBLE_BY_DECISION,

    /** Recoverable from party. */
    RECOVERABLE_FROM_PARTY
}
