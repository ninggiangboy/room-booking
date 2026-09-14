package dev.ngb.backend.hostops.internal.model.benchmark;

/**
 * Whether a market benchmark carries numbers or was suppressed. A suppressed benchmark is still a
 * row: absence is indistinguishable from a pipeline failure, and a host told nothing learns the
 * wrong thing from the silence.
 */
public enum BenchmarkPublicationState {

    /** The aggregate cleared its cohort floors and carries its quartiles. */
    PUBLISHED,

    /** The aggregate carries a reason instead of numbers. */
    SUPPRESSED
}
