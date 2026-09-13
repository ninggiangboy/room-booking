package dev.ngb.backend.model;

/**
 * Who the decision was taken by or on behalf of.
 */
public enum DecisionActorKind {

    /** Taken automatically under policy. */
    SYSTEM,

    /** Taken by a member of staff. */
    OPERATOR,

    /** Taken by the guest. */
    GUEST,

    /** Taken by the host. */
    HOST
}
