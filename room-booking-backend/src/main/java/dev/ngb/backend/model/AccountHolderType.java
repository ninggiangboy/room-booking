package dev.ngb.backend.model;

/**
 * Whether an account holder is a natural person or an organization.
 *
 * <p>The distinction is not cosmetic: an organization owns supply and receives settlement through
 * members who are not itself, so it cannot borrow a single user's identity. A person holder is
 * one-to-one with its {@code users} row; an organization exists independently of any user.</p>
 */
public enum AccountHolderType {
    /** A natural person, backed by exactly one user account. */
    PERSON,
    /** A company or other entity acting through its members. */
    ORGANIZATION
}
