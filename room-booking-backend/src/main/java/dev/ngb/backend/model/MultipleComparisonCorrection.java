package dev.ngb.backend.model;

/**
 * How the analysis pays for testing several metrics at once.
 */
public enum MultipleComparisonCorrection {

    /** One primary metric, no correction needed. */
    NONE,

    /** Error budget split evenly across tests. */
    BONFERRONI,

    /** False-discovery-rate control. */
    BENJAMINI_HOCHBERG,

    /** Stepwise variant of Bonferroni. */
    HOLM
}
