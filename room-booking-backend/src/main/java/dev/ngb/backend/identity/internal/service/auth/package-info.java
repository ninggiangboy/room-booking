/**
 * Registration and credential-login orchestration, and the token-construction factory shared by
 * this package's three workflow subpackages.
 *
 * <p>{@code auth.session} (refresh-token rotation and reuse detection), {@code auth.verification}
 * (email verification), and {@code auth.passwordreset} (password reset) each own one live
 * workflow. {@code AuthTokenFactory} lives here, one level up from all three, because every one of
 * them needs to build a token row; it is {@code public} for that reason. {@code
 * UserRegistrationFactory} stays package-private: only {@link AuthenticationService}, in this same
 * package, ever constructs a new account.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity.internal.service.auth;
