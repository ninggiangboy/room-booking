package dev.ngb.backend.ml.internal.model.label;

/**
 * Whether a corrected outcome becomes a new revision or a new version.
 */
public enum LabelCorrectionBehaviour {

    /** A corrected outcome is a new revision of the same example. */
    NEW_REVISION,

    /** A corrected outcome requires a new label definition version. */
    NEW_VERSION
}
