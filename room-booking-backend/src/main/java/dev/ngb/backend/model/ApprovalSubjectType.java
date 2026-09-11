package dev.ngb.backend.model;

/**
 * Kind of configuration a market approval record decides upon.
 *
 * <p>The subject is referenced by type and identifier rather than by a foreign key per kind, so one
 * approval trail covers every kind of configuration without a column per table.</p>
 */
public enum ApprovalSubjectType {
    /** A market's activation. */
    MARKET,
    /** A legal entity's accountability. */
    LEGAL_ENTITY,
    /** A version of an approved rule set. */
    POLICY_BUNDLE,
    /** Availability of a capability, method, or rail. */
    MARKET_CAPABILITY,
    /** An external integration context. */
    PROVIDER_ACCOUNT,
    /** A reviewed localized content version. */
    LOCALIZED_CONTENT
}
