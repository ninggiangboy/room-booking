/**
 * Authentication, JWT, refresh-token, registration, email-verification, and password-recovery
 * workflows.
 *
 * <p>Package-private methods and classes intentionally limit sensitive token operations to trusted
 * collaborators in this package: {@code AuthTokenFactory} and {@code UserRegistrationFactory} are
 * package-private, so a raw token secret or a partially constructed user can never escape here.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity.internal.service.auth;
