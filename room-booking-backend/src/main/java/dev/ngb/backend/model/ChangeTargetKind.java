package dev.ngb.backend.model;

/**
 * What a change request is about.
 * <p>A configured value, a feature flag and an artifact published by another domain travel the same
 * maker-checker path, so that the approval trail for publishing a tax rule and for changing a
 * timeout look the same to whoever has to audit them.</p>
 */
public enum ChangeTargetKind {

    /** Changes a configured value. */
    CONFIGURATION,

    /** Changes the state of a feature flag. */
    FEATURE_FLAG,

    /** Publishes a versioned artifact another domain owns, such as a policy or tax rule. */
    GOVERNED_ARTIFACT
}
