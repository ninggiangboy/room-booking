package dev.ngb.backend.model;

/**
 * How a notice reaches somebody.
 *
 * <p>The target default is in-app first, then transactional email, then support escalation when no
 * routable critical destination exists. Push and SMS are later channels.</p>
 */
public enum NotificationChannel {
    /** The durable in-app inbox projection. */
    IN_APP,
    /** Transactional email. */
    EMAIL,
    /** Short message service. */
    SMS,
    /** Device push notification. */
    PUSH
}
