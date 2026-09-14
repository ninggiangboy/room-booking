package dev.ngb.backend.ml.internal.model.model;

/**
 * Whether a reviewing function approved or rejected a model version.
 */
public enum ModelApprovalDecision {

    /** The function signed off, within the scope it recorded. */
    APPROVED,

    /** The function refused. */
    REJECTED
}
