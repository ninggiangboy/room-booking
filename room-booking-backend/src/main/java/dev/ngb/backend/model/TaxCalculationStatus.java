package dev.ngb.backend.model;

/**
 * Whether a tax calculation still stands.
 *
 * <p>{@code FAILED} is recorded rather than discarded: an attempt that could not produce a figure is
 * itself evidence about why a quote was refused, and dropping it leaves a gap nobody can explain
 * later.</p>
 */
public enum TaxCalculationStatus {
    /** Produced a result that is currently authoritative. */
    CALCULATED,
    /** Replaced by a later calculation for the same subject. */
    SUPERSEDED,
    /** Withdrawn because it should not have been applied. */
    VOID,
    /** Did not produce a usable result. */
    FAILED
}
