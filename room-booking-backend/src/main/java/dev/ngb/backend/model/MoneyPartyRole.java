package dev.ngb.backend.model;

/**
 * A party's relationship to one money line.
 *
 * <p>Named for money specifically, to keep it clear of {@link Role}, which is about what someone may
 * do in the product. Who pays, who receives, and who funds are three separate questions about the
 * same amount, and a single "party" column collapsing them is how a fee reaches the wrong payout.</p>
 */
public enum MoneyPartyRole {
    /** The guest taking the stay. */
    GUEST,
    /** The host supplying it. */
    HOST,
    /** The marketplace itself. */
    PLATFORM,
    /** A tax authority or other body entitled to the amount. */
    AUTHORITY
}
