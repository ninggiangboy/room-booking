package dev.ngb.backend.support.internal.model.case_;

/**
 * The visibility scope of {@code case_contacts}.
 */
public enum ContactVisibility {

    /** Participant shared. */
    PARTICIPANT_SHARED,

    /** Sender and recipient. */
    SENDER_AND_RECIPIENT,

    /** Internal only. */
    INTERNAL_ONLY,

    /** Restricted authority. */
    RESTRICTED_AUTHORITY
}
