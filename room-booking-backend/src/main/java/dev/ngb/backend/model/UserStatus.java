package dev.ngb.backend.model;

/** Lifecycle states used instead of physically deleting user records. */
public enum UserStatus {
    /** Account may authenticate and perform normal business operations. */
    ACTIVE,
    /** Account is retained but temporarily blocked by the platform. */
    SUSPENDED,
    /** Account is logically deleted while historical references remain intact. */
    DELETED
}
