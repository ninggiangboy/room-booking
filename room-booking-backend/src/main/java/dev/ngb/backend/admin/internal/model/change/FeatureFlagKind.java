package dev.ngb.backend.admin.internal.model.change;

/**
 * What a feature flag is for.
 * <p>A release flag is temporary scaffolding, an operational flag is a dial, a kill switch is a
 * brake and a permission flag gates access. They are separated because the rules about who may move
 * them, and in which direction, differ.</p>
 */
public enum FeatureFlagKind {

    /** Temporary scaffolding around something being rolled out. */
    RELEASE,

    /** A dial the people running the platform turn. */
    OPERATIONAL,

    /** Carries an experiment variant. */
    EXPERIMENT,

    /** Gates access to something. */
    PERMISSION,

    /** A brake; always off by default and always pullable without a second person. */
    KILL_SWITCH
}
