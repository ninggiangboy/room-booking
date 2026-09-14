package dev.ngb.backend.support.internal.model.case_;

/**
 * The visibility scope of {@code case_timeline_entries}.
 */
public enum CaseTimelineVisibility {

    /** Internal only. */
    INTERNAL_ONLY,

    /** Participant shared. */
    PARTICIPANT_SHARED,

    /** Sender and recipient. */
    SENDER_AND_RECIPIENT,

    /** Restricted authority. */
    RESTRICTED_AUTHORITY
}
