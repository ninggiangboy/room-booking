package dev.ngb.backend.model;

/**
 * Which kind of party a tax profile describes.
 *
 * <p>The two are stored in one table with one populated reference each, because the questions asked
 * of them — residence, establishment, registrations — are identical, while the row they point at is
 * not.</p>
 */
public enum TaxPartyType {
    /** A guest or host account. */
    ACCOUNT_HOLDER,
    /** One of the platform's own contracting entities. */
    LEGAL_ENTITY
}
