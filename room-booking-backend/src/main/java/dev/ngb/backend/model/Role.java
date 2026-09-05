package dev.ngb.backend.model;

/**
 * Authorities that control what a user may do in the platform.
 *
 * <p>The JWT filter converts each value to Spring Security's conventional {@code ROLE_*}
 * authority, for example {@code GUEST} becomes {@code ROLE_GUEST}.</p>
 */
public enum Role {
    /** User who can search for and reserve rooms. */
    GUEST,
    /** User who can publish and manage listings. */
    HOST,
    /** Platform operator with administrative capabilities. */
    ADMIN
}
