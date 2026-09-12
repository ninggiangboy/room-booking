package dev.ngb.backend.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A stay's nights as a half-open civil-date range, {@code [checkIn, checkOut)}.
 *
 * <p>Half-open is the whole point. A guest checking out on the 5th and a guest checking in on the 5th
 * do not conflict, and any model that treats the range as closed either loses a sellable night on
 * every booking or double-books the changeover day. The type exists so that invariant is stated once
 * here and enforced once in the database, rather than re-derived at every call site.</p>
 *
 * <p>These are civil dates in the property's own time zone, never instants. "The night of the 3rd"
 * is a fact about the property's calendar, not about any particular moment in UTC — converting them
 * requires the property's zone and belongs in {@code StayCalendar}.</p>
 *
 * <p>Stored as a PostgreSQL {@code daterange}, which is what lets the database enforce non-overlap
 * with a GiST exclusion constraint.</p>
 *
 * @param checkIn first night of the stay, inclusive
 * @param checkOut departure date, exclusive; the first night the space is free again
 */
public record StayRange(LocalDate checkIn, LocalDate checkOut) {

    /**
     * Creates a stay range, rejecting one that covers no nights.
     *
     * @param checkIn first night of the stay
     * @param checkOut departure date, which must be strictly later
     */
    public StayRange {
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException(
                    "checkOut must be after checkIn: " + checkIn + " to " + checkOut);
        }
    }

    /**
     * Parses the PostgreSQL {@code daterange} text form.
     *
     * <p>Only the canonical half-open form is accepted. PostgreSQL normalizes every discrete range to
     * {@code [inclusive,exclusive)} on storage, so anything else indicates a value this application
     * did not write.</p>
     *
     * @param text range in {@code [YYYY-MM-DD,YYYY-MM-DD)} form
     * @return the parsed stay range
     * @throws IllegalArgumentException when the text is not a canonical half-open date range
     */
    public static StayRange parse(String text) {
        if (text.length() < 3 || text.charAt(0) != '[' || text.charAt(text.length() - 1) != ')') {
            throw new IllegalArgumentException("Not a canonical half-open daterange: " + text);
        }
        String body = text.substring(1, text.length() - 1);
        int comma = body.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("Not a canonical half-open daterange: " + text);
        }
        return new StayRange(
                LocalDate.parse(body.substring(0, comma).trim()),
                LocalDate.parse(body.substring(comma + 1).trim()));
    }

    /**
     * Renders the PostgreSQL {@code daterange} text form.
     *
     * @return the range as {@code [checkIn,checkOut)}
     */
    public String toRangeLiteral() {
        return "[" + checkIn + "," + checkOut + ")";
    }

    /**
     * Returns how many nights the stay covers.
     *
     * <p>Equal to the number of dates in the half-open range, which is why a one-night stay spans two
     * calendar dates.</p>
     *
     * @return number of nights, always at least one
     */
    public long nights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    /**
     * Reports whether this range shares at least one night with another.
     *
     * <p>Mirrors the database's {@code &&} operator on {@code daterange}, so a caller checking in
     * Java reaches the same conclusion the exclusion constraint would. Touching ranges — one's
     * checkout equal to the other's check-in — do not overlap.</p>
     *
     * @param other range to compare against
     * @return {@code true} when the two share a night
     */
    public boolean overlaps(StayRange other) {
        return checkIn.isBefore(other.checkOut) && other.checkIn.isBefore(checkOut);
    }

    /**
     * Renders the range for logs and messages.
     *
     * @return the canonical range literal
     */
    @Override
    public String toString() {
        return toRangeLiteral();
    }
}
