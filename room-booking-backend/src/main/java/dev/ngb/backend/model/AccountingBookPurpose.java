package dev.ngb.backend.model;

/**
 * What a set of accounts is kept for.
 *
 * <p>A book is scoped to one legal entity and one purpose. Every journal row names its book, so a
 * second book can be added later without any existing entry becoming ambiguous about which view of
 * the business it belongs to.</p>
 */
public enum AccountingBookPurpose {
    /** The operational journal of marketplace activity. */
    MARKETPLACE_SUBLEDGER,
    /** A book kept to satisfy a jurisdiction's reporting requirements. */
    STATUTORY,
    /** A book kept for internal reporting rather than filing. */
    MANAGEMENT,
    /** A book reconciling one external provider's position. */
    PROVIDER_CONTROL
}
