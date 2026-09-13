package dev.ngb.backend.model;

/**
 * The breach behaviour of {@code remedy_budget_windows}.
 */
public enum BudgetBreachBehaviour {

    /** Refuse. */
    REFUSE,

    /** Require approval. */
    REQUIRE_APPROVAL,

    /** Escalate. */
    ESCALATE
}
