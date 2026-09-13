package dev.ngb.backend.model;

/**
 * Which kind of entitlement a decision rests on.
 *
 * <p>Contractual, protection and goodwill are kept apart because goodwill never changes historical price
 * or tax facts and never rewrites contract liability.</p>
 */
public enum DecisionBasis {

    /** Amount due under the accepted booking or cancellation terms, or mandatory legal policy. */
    CONTRACTUAL,

    /** Amount due under an applicable protection programme or policy. */
    PROTECTION_OR_CLAIM,

    /** Discretionary service recovery that does not rewrite contract liability. */
    GOODWILL,

    /** More than one basis contributes, each carried on its own line. */
    MIXED,

    /** The decision moves no money. */
    NOT_APPLICABLE
}
