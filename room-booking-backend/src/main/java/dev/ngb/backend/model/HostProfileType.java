package dev.ngb.backend.model;

/**
 * Whether a selling host is a natural person or a registered business.
 *
 * <p>The two carry structurally different evidence: an individual has a date of birth, a business has
 * an incorporation date, a registration number, and beneficial owners. The database refuses the mixed
 * row, because an age check that silently passes on a company that has no age is worse than one that
 * fails outright.</p>
 */
public enum HostProfileType {
    /** A natural person selling in their own name. */
    INDIVIDUAL,
    /** A registered company or other legal entity. */
    BUSINESS
}
