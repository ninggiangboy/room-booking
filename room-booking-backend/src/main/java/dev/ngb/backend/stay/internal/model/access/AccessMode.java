package dev.ngb.backend.stay.internal.model.access;

/**
 * How a grant is actually honoured at the door.
 */
public enum AccessMode {
    /** Provider-issued credential on a connected lock. */
    SMART_LOCK,
    /** Provider-issued numeric code. */
    KEYPAD_CODE,
    /** Provider-issued credential held on a device. */
    MOBILE_KEY,
    /** Physical key behind a verified lockbox. */
    LOCKBOX,
    /** Key handed over by a person. */
    IN_PERSON_HANDOFF,
    /** Collection at a staffed desk. */
    FRONT_DESK
}
