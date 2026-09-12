package dev.ngb.backend.model;

/**
 * What actually happened at the property, as distinct from what was agreed.
 *
 * <p>Confirmation grants a future contract; it does not prove arrival. This dimension is therefore
 * separate from {@link BookingLifecycleState}, and the database refuses to let it move while the
 * booking is still provisional: a guest cannot arrive at a stay nobody has confirmed.</p>
 */
public enum StayState {
    /** The stay has not begun. */
    NOT_STARTED,
    /** Arrival has been evidenced. */
    CHECKED_IN,
    /** Departure has been evidenced. */
    CHECKED_OUT,
    /** The guest did not arrive, decided on evidence rather than on an absent app click. */
    NO_SHOW
}
