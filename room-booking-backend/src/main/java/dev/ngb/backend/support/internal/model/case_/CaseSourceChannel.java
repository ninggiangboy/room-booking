package dev.ngb.backend.support.internal.model.case_;

/**
 * The source channel of {@code support_cases}.
 */
public enum CaseSourceChannel {

    /** Web. */
    WEB,

    /** Mobile app. */
    MOBILE_APP,

    /** Email. */
    EMAIL,

    /** Phone. */
    PHONE,

    /** Chat. */
    CHAT,

    /** In stay flow. */
    IN_STAY_FLOW,

    /** Partner. */
    PARTNER,

    /** Internal. */
    INTERNAL,

    /** Automated detection. */
    AUTOMATED_DETECTION,

    /** Regulator. */
    REGULATOR,

    /** Legacy import. */
    LEGACY_IMPORT
}
