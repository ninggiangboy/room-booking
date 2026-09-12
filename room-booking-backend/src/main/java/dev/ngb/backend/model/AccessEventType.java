package dev.ngb.backend.model;

/**
 * What a lock or its provider reported.
 */
public enum AccessEventType {
    /** A credential now exists. */
    CREDENTIAL_CREATED,
    /** A credential was replaced. */
    CREDENTIAL_ROTATED,
    /** A credential was withdrawn. */
    CREDENTIAL_REVOKED,
    /** A credential lapsed. */
    CREDENTIAL_EXPIRED,
    /** The door opened. Who opened it is a separate question. */
    DOOR_OPENED,
    /** The door locked. */
    DOOR_LOCKED,
    /** A credential was presented and refused. */
    ACCESS_DENIED,
    /** The device stopped responding. */
    DEVICE_OFFLINE,
    /** The device warned about power. */
    DEVICE_BATTERY_LOW,
    /** The provider reported something we do not model. */
    UNKNOWN
}
