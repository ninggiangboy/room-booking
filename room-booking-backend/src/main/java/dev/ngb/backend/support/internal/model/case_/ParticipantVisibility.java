package dev.ngb.backend.support.internal.model.case_;

/**
 * The visibility scope of {@code case_participants}.
 */
public enum ParticipantVisibility {

    /** None. */
    NONE,

    /** Own submissions. */
    OWN_SUBMISSIONS,

    /** Participant shared. */
    PARTICIPANT_SHARED,

    /** Full case. */
    FULL_CASE,

    /** Internal full. */
    INTERNAL_FULL,

    /** Restricted authority. */
    RESTRICTED_AUTHORITY
}
