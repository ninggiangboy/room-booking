package dev.ngb.backend.support.internal.model.case_;

/**
 * The clock type of {@code case_sla_clocks}.
 */
public enum SlaClockType {

    /** Acknowledgement. */
    ACKNOWLEDGEMENT,

    /** First human response. */
    FIRST_HUMAN_RESPONSE,

    /** Next action. */
    NEXT_ACTION,

    /** Host response. */
    HOST_RESPONSE,

    /** Guest response. */
    GUEST_RESPONSE,

    /** Evidence submission. */
    EVIDENCE_SUBMISSION,

    /** Decision. */
    DECISION,

    /** Remedy execution. */
    REMEDY_EXECUTION,

    /** Appeal. */
    APPEAL,

    /** Provider dispute. */
    PROVIDER_DISPUTE,

    /** Protection submission. */
    PROTECTION_SUBMISSION
}
