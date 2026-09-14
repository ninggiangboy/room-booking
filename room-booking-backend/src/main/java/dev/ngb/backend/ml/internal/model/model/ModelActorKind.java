package dev.ngb.backend.ml.internal.model.model;

/**
 * Who issued a model command.
 *
 * <p>Monitoring automation may pause or roll back and may never promote.</p>
 */
public enum ModelActorKind {

    /** A named person. */
    OPERATOR,

    /** A guardrail that may stop a model and may never ship one. */
    MONITORING_AUTOMATION
}
