package dev.ngb.backend.model;

/**
 * An experiment variant's buckets as a half-open integer range, {@code [low, high)}.
 *
 * <p>Half-open is what makes the arms of an epoch tile cleanly. Contiguous ranges written as
 * {@code [0,5000)} and {@code [5000,10000)} share no bucket, where an inclusive spelling would
 * either lose bucket 5000 or hand it to both arms -- and a unit in two arms is not a configuration
 * mistake, it is a unit receiving two treatments while the analysis assumes it received one.</p>
 *
 * <p>The bucket is the output of the assignment hash, so a range is a claim about which hash values
 * belong to an arm. It is meaningful only alongside the epoch's salt version, allocator version and
 * bucket count, which is why all three are stored on the epoch and repeated on every assignment.</p>
 *
 * <p>Stored as a PostgreSQL {@code int4range}, which is what lets the database refuse two
 * overlapping arms with a GiST exclusion constraint rather than leaving it to review.</p>
 *
 * @param low first bucket in the arm, inclusive
 * @param high first bucket past the arm, exclusive
 */
public record BucketRange(int low, int high) {

    /**
     * Creates a bucket range, rejecting one that holds no buckets or starts below zero.
     *
     * @param low first bucket in the arm
     * @param high first bucket past it, which must be strictly greater
     */
    public BucketRange {
        if (low < 0) {
            throw new IllegalArgumentException("low must not be negative: " + low);
        }
        if (high <= low) {
            throw new IllegalArgumentException("high must be above low: " + low + " to " + high);
        }
    }

    /**
     * Parses the PostgreSQL {@code int4range} text form.
     *
     * <p>Only the canonical half-open form is accepted. PostgreSQL normalizes every discrete range
     * to {@code [inclusive,exclusive)} on storage, so anything else indicates a value this
     * application did not write.</p>
     *
     * @param text range in {@code [low,high)} form
     * @return the parsed bucket range
     * @throws IllegalArgumentException when the text is not a canonical half-open integer range
     */
    public static BucketRange parse(String text) {
        if (text.length() < 3 || text.charAt(0) != '[' || text.charAt(text.length() - 1) != ')') {
            throw new IllegalArgumentException("Not a canonical half-open int4range: " + text);
        }
        String body = text.substring(1, text.length() - 1);
        int comma = body.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("Not a canonical half-open int4range: " + text);
        }
        return new BucketRange(
                Integer.parseInt(body.substring(0, comma).trim()),
                Integer.parseInt(body.substring(comma + 1).trim()));
    }

    /**
     * Renders the PostgreSQL {@code int4range} text form.
     *
     * @return the range as {@code [low,high)}
     */
    public String toRangeLiteral() {
        return "[" + low + "," + high + ")";
    }

    /**
     * Returns how many buckets the arm holds.
     *
     * @return the bucket count, always at least one
     */
    public int size() {
        return high - low;
    }

    /**
     * Reports whether a bucket falls in this arm.
     *
     * <p>Mirrors the database's {@code @>} operator on {@code int4range}, so a caller checking in
     * Java reaches the same conclusion the assignment trigger would.</p>
     *
     * @param bucket hash output to test
     * @return {@code true} when the bucket belongs to this arm
     */
    public boolean contains(int bucket) {
        return bucket >= low && bucket < high;
    }

    /**
     * Reports whether this arm shares a bucket with another.
     *
     * <p>Mirrors the {@code &&} operator that the epoch's exclusion constraint is built on.
     * Touching ranges -- one arm's upper bound equal to the other's lower -- do not overlap.</p>
     *
     * @param other range to compare against
     * @return {@code true} when the two share a bucket
     */
    public boolean overlaps(BucketRange other) {
        return low < other.high && other.low < high;
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
