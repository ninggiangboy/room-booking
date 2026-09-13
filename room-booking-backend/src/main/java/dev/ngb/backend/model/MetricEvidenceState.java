package dev.ngb.backend.model;

/**
 * Whether a computed figure had enough evidence behind it to be shown as a number. The number and
 * the admission that there is no number are mutually exclusive, which is what keeps a rate over
 * four observations from being shown as a rate.
 */
public enum MetricEvidenceState {

    /** Enough observations to work the number out, and the number is on the row. */
    SUFFICIENT,

    /** Too few observations; the published sentence is shown instead of a number. */
    INSUFFICIENT,

    /** Withheld for a privacy or policy reason rather than a shortage of data. */
    SUPPRESSED,

    /** The figure could not be produced at all, and the host is told so rather than shown a gap. */
    UNAVAILABLE
}
