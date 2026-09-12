package dev.ngb.backend.model;

/**
 * How much of the space the guest shares with others.
 *
 * <p>Held separately from {@link RoomType} because the same room type can mean different things: a
 * private room may come with a private bathroom or a shared one, and a guest choosing between them
 * needs the distinction stated rather than implied.</p>
 */
public enum SpaceSharing {
    /** Nothing is shared with anyone outside the booking party. */
    EXCLUSIVE,
    /** Common areas such as a kitchen or lounge are shared. */
    SHARED_COMMON,
    /** Sleeping space as well as common areas is shared. */
    SHARED_ALL
}
