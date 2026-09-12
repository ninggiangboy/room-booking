package dev.ngb.backend.model;

/**
 * Progress of one due component.
 *
 * <p>A later component failing does not undo what an earlier one bought. A defaulted balance starts
 * a dunning workflow; it does not erase the confirmation the deposit produced.</p>
 */
public enum ScheduleItemState {
    /** Due, with nothing collected. */
    OPEN,
    /** An attempt against this component is in flight. */
    PROCESSING,
    /** Collected in full. */
    SATISFIED,
    /** Forgiven by an approved decision. */
    WAIVED,
    /** Past its final deadline without being collected. */
    DEFAULTED,
    /** Withdrawn, typically superseded by a replacement schedule version. */
    CANCELLED
}
