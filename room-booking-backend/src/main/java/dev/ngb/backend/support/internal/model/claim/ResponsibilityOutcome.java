package dev.ngb.backend.support.internal.model.claim;

/**
 * Who, if anybody, the evidence shows was responsible.
 *
 * <p>{@code UNDETERMINED} and {@code INSUFFICIENT_EVIDENCE} are answers in their own right and cannot
 * support an adverse monetary decision.</p>
 */
public enum ResponsibilityOutcome {

    /** Not yet answered; cannot support an adverse monetary decision. */
    UNDETERMINED,

    /** The guest or somebody they brought. */
    GUEST_OR_VISITOR,

    /** A maintenance obligation the host held. */
    HOST_MAINTENANCE,

    /** The condition was already there before the stay. */
    PRE_EXISTING,

    /** Ordinary wear rather than damage. */
    ORDINARY_WEAR,

    /** Somebody outside the booking. */
    THIRD_PARTY,

    /** More than one party contributed. */
    SHARED,

    /** The evidence cannot settle it; not the same as finding nobody responsible. */
    INSUFFICIENT_EVIDENCE,

    /** Responsibility does not arise on this claim. */
    NOT_APPLICABLE
}
