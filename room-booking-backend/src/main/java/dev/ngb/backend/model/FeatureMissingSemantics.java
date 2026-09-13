package dev.ngb.backend.model;

/**
 * What a feature means when it cannot be computed.
 *
 * <p>Required of any feature served on the critical path. Without it a vendor timeout silently
 * becomes a zero, and a zero silently becomes safe.</p>
 */
public enum FeatureMissingSemantics {
    /** The value is recorded as unknown and policy handles it. */
    EXPLICIT_UNKNOWN,
    /** A registered fallback value applies. */
    POLICY_FALLBACK,
    /** The evaluation cannot proceed without it. */
    BLOCK_EVALUATION;
}
