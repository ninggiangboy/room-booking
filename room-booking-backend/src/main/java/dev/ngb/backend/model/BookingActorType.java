package dev.ngb.backend.model;

/**
 * Kind of actor responsible for a booking transition or timeline entry.
 *
 * <p>Deliberately separate from {@link ActorType}, which serves platform-wide audit and speaks of
 * users and operators. A booking's history needs to distinguish the guest from the host, because
 * which of the two parties acted is the first question any dispute asks.</p>
 */
public enum BookingActorType {
    /** The guest on the booking. */
    GUEST,
    /** The host of the supply. */
    HOST,
    /** A support agent acting under delegated authority. */
    SUPPORT_AGENT,
    /** An on-path platform process. */
    SYSTEM,
    /** A background worker, typically a sweeper acting on an elapsed deadline. */
    WORKER,
    /** An external provider reporting a verified outcome. */
    PROVIDER
}
