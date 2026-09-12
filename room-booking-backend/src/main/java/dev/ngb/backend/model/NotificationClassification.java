package dev.ngb.backend.model;

/**
 * What kind of notice a purpose produces, which decides whether consent and quiet hours apply.
 */
public enum NotificationClassification {
    /** Security, booking-critical, safety or required legal notices. */
    TRANSACTIONAL_MANDATORY,
    /** Service reminders a recipient may configure. */
    TRANSACTIONAL_OPTIONAL,
    /** Promotion, re-engagement or campaign purposes, suppressed immediately on withdrawal. */
    MARKETING
}
