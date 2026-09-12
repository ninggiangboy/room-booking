package dev.ngb.backend.model;

/**
 * Who a reputation purpose is computed for.
 */
public enum ReputationActorRole {
    /** A host making a decision. */
    HOST,
    /** A guest making a decision. */
    GUEST,
    /** An operator handling a case. */
    OPERATOR,
    /** An automated consumer. */
    SYSTEM
}
