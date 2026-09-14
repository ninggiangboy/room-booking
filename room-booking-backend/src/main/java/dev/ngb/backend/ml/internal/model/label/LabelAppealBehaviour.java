package dev.ngb.backend.ml.internal.model.label;

/**
 * What an appeal against the underlying decision does to the label.
 */
public enum LabelAppealBehaviour {

    /** A successful appeal produces a new reading of the outcome. */
    REOPENS_LABEL,

    /** The appeal is recorded beside the outcome but does not change it. */
    RECORDED_ONLY,

    /** Nothing about this target can be appealed. */
    NOT_APPLICABLE
}
