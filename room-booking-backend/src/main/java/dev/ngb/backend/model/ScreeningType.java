package dev.ngb.backend.model;

/**
 * Which list or rule a host was screened against.
 *
 * <p>Screenings are recorded separately by type because they expire on different schedules and carry
 * different consequences: an age check settles once, while sanctions screening must be repeated for
 * as long as the host keeps selling.</p>
 */
public enum ScreeningType {
    /** Government sanctions lists. */
    SANCTIONS,
    /** Politically exposed persons. */
    PEP,
    /** Other watch lists. */
    WATCHLIST,
    /** Negative news coverage. */
    ADVERSE_MEDIA,
    /** Minimum age for the market. */
    AGE,
    /** A rule specific to the market's own regulator. */
    MARKET_SPECIFIC
}
