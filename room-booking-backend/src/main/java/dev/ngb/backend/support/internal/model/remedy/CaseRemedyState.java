package dev.ngb.backend.support.internal.model.remedy;

/**
 * Where an authorized remedy stands between proposal and effect.
 *
 * <p>{@code APPROVED} means entitlement was authorized; only a downstream {@code SUCCEEDED} proves the
 * money moved. {@code SUCCEEDED} and {@code PARTIAL} reach {@code REVERSED_OR_RECOVERED} only through a
 * new authorized decision.</p>
 */
public enum CaseRemedyState {

    /** Draft. */
    DRAFT,

    /** Proposed. */
    PROPOSED,

    /** Approval pending. */
    APPROVAL_PENDING,

    /** Approved. */
    APPROVED,

    /** Rejected. */
    REJECTED,

    /** Expired. */
    EXPIRED,

    /** Instruction pending. */
    INSTRUCTION_PENDING,

    /** Executing. */
    EXECUTING,

    /** Succeeded. */
    SUCCEEDED,

    /** Partial. */
    PARTIAL,

    /** Unknown. */
    UNKNOWN,

    /** Reconciling. */
    RECONCILING,

    /** Reversed or recovered. */
    REVERSED_OR_RECOVERED
}
