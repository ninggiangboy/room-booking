package dev.ngb.backend.model;

/**
 * How far a finance reconciliation case has got.
 *
 * <p>A case may be reopened when contradictory evidence arrives. Its {@code resolvedAt} is not
 * cleared when that happens: the case genuinely was resolved once, and that history is part of what
 * a later reviewer needs.</p>
 */
public enum FinanceCaseState {
    /** Raised and unowned. */
    OPEN,
    /** Classified and prioritised. */
    TRIAGED,
    /** Owned by a person. */
    ASSIGNED,
    /** Being worked. */
    INVESTIGATING,
    /** An answer is proposed and awaits approval. */
    RESOLUTION_PROPOSED,
    /** Raised beyond the assigned owner. */
    ESCALATED,
    /** Answered, with evidence and any approved correction linked. */
    RESOLVED,
    /** Contradictory evidence arrived after resolution. */
    REOPENED
}
