package dev.ngb.backend.analytics.internal.model.experiment;

/**
 * How repeated looks at a running experiment are paid for.
 *
 * <p>NONE means one look. Stopping on an unadjusted p-value after several is peeking, not a
 * sequential policy.</p>
 */
public enum SequentialMethod {

    /** One look only. */
    NONE,

    /** Error budget spent across a planned schedule of looks. */
    ALPHA_SPENDING,

    /** Always-valid sequential test. */
    MIXTURE_SPRT,

    /** A fixed number of interim analyses with adjusted boundaries. */
    GROUP_SEQUENTIAL
}
