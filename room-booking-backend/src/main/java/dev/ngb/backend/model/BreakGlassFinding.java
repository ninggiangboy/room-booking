package dev.ngb.backend.model;

/**
 * What the post-use review concluded about the access that was actually taken. Anything other than
 * {@code APPROPRIATE} requires a follow-up to point at.
 */
public enum BreakGlassFinding {

    /** The access matched the emergency and what was done under it was warranted. */
    APPROPRIATE,

    /** More was reached than the emergency required. */
    EXCESSIVE_SCOPE,

    /** The emergency did not warrant breaking the glass at all. */
    UNJUSTIFIED,

    /** What was done under the access breached policy. */
    POLICY_BREACH
}
