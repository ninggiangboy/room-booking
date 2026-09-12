package dev.ngb.backend.model;

/**
 * Who is obliged to hand the tax to the authority.
 *
 * <p>The same amount shown to a guest becomes a different obligation depending on this: it decides
 * which entity files, which ledger accounts are posted, and whose registration number appears on the
 * return. A line claiming {@code MARKETPLACE_LIABLE} while naming the host as remitter is refused by
 * the database, because one of the two statements must be wrong.</p>
 */
public enum TaxRemittanceModel {
    /** The marketplace is liable and files the tax itself. */
    MARKETPLACE_LIABLE,
    /** The host is liable and files it. */
    SUPPLIER_LIABLE,
    /** The amount is withheld from a party's proceeds and remitted on their behalf. */
    WITHHOLDING,
    /** Collected on behalf of another party who remits it. */
    PASS_THROUGH
}
