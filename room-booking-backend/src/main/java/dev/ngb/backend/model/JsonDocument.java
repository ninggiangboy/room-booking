package dev.ngb.backend.model;

/**
 * A JSON value destined for a PostgreSQL {@code jsonb} column.
 *
 * <p>The type exists because a bare {@link String} cannot be bound to {@code jsonb}: PostgreSQL
 * refuses the implicit cast, and widening the conversion to every string in the application would
 * turn ordinary text columns into JSON ones. Giving the value its own type keeps the conversion
 * narrow and makes the intent visible at the field declaration.</p>
 *
 * <p>Per the persistence conventions, {@code jsonb} is limited to immutable historical snapshots and
 * bounded safe projections. Anything that must be filtered, joined, or constrained stays relational,
 * so this type should never hold live business state.</p>
 *
 * @param value serialized JSON text; the database validates that it parses
 */
public record JsonDocument(String value) {

    /**
     * Wraps serialized JSON text, rejecting a blank value that {@code jsonb} would not accept.
     *
     * @param value serialized JSON text
     */
    public JsonDocument {
        if (value.isBlank()) {
            throw new IllegalArgumentException("JSON document must not be blank");
        }
    }

    /**
     * Returns the serialized JSON text.
     *
     * @return the JSON text this document wraps
     */
    @Override
    public String toString() {
        return value;
    }
}
