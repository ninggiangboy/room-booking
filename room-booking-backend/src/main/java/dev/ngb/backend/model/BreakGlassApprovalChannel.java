package dev.ngb.backend.model;

/**
 * How an emergency approval was given.
 * <p>Approval out of band is legitimate at three in the morning, and recording which band it came
 * through is what lets the post-use review judge it.</p>
 */
public enum BreakGlassApprovalChannel {

    /** Approved in the administrative console, the ordinary way. */
    CONSOLE,

    /** Approved by whoever was paged. */
    ON_CALL_PAGE,

    /** Approved verbally and written down immediately afterwards. */
    VOICE,

    /** Approved by a standing written order covering this situation. */
    WRITTEN_ORDER
}
