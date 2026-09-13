package dev.ngb.backend.model;

/**
 * The decided by actor type of {@code case_decisions}.
 */
public enum DecisionActorType {

    /** Agent. */
    AGENT,

    /** Supervisor. */
    SUPERVISOR,

    /** Specialist. */
    SPECIALIST,

    /** Adjuster. */
    ADJUSTER,

    /** Legal. */
    LEGAL,

    /** Deterministic evaluator. */
    DETERMINISTIC_EVALUATOR
}
