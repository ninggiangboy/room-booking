package dev.ngb.backend.model;

/** Stages of the identity-review process for a host. */
public enum IdentityStatus {
    /** The host has not submitted identity evidence. */
    UNVERIFIED,
    /** Evidence has been submitted and awaits review. */
    PENDING,
    /** The platform accepted the host's identity evidence. */
    VERIFIED,
    /** The submitted evidence did not pass review. */
    REJECTED
}
