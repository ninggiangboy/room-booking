package dev.ngb.backend.model;

/**
 * What part a metric plays in one experiment.
 *
 * <p>The primary metric is what the decision rule is written against; guardrails are what make a
 * gain bought at somebody else expense visible in the same analysis.</p>
 */
public enum MetricRole {

    /** What the decision rule is written against. */
    PRIMARY,

    /** Informative, not decisive. */
    SECONDARY,

    /** A limit that a gain may not be bought at the expense of. */
    GUARDRAIL
}
