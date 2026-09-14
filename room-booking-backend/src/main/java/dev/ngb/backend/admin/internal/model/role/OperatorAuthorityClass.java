package dev.ngb.backend.admin.internal.model.role;

/**
 * How far an operator role reaches, which is what decides how many approvals an assignment of it
 * needs and whether it may ever be held under emergency access.
 */
public enum OperatorAuthorityClass {

    /** Sees the marketplace and changes nothing. */
    READ_ONLY,

    /** Works guest and host cases, reaching personal data within one case. */
    SUPPORT,

    /** Changes running state -- pausing, retrying, reassigning. */
    OPERATIONAL,

    /** Moves money: refunds, adjustments, payout holds. */
    FINANCIAL,

    /** Reaches identity documents and verification outcomes. */
    IDENTITY,

    /** Acts on safety reports and enforcement. */
    SAFETY,

    /** Administers the platform itself, including who else may do any of the above. */
    PLATFORM_ADMIN
}
