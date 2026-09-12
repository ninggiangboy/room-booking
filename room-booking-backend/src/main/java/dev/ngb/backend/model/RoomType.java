package dev.ngb.backend.model;

/**
 * What a guest actually gets when they book.
 *
 * <p>{@link #ENTIRE_PLACE} is a promise of exclusivity, which is why the database refuses to pair it
 * with any shared-space value: a guest booking what they believe is a private home and finding
 * strangers in the kitchen is not a display bug.</p>
 */
public enum RoomType {
    /** The whole property, exclusively. */
    ENTIRE_PLACE,
    /** A private room in a property with shared common areas. */
    PRIVATE_ROOM,
    /** A room shared with other guests. */
    SHARED_ROOM,
    /** A single bed in a shared dormitory. */
    DORM_BED
}
