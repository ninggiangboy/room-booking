/**
 * Refresh-token issuance, rotation, reuse detection, and revocation, backed by durable
 * {@code auth_sessions}.
 *
 * <p>{@code RefreshTokenService}'s token-rotation and session-lookup methods are {@code public}
 * rather than package-private because they are called from sibling packages
 * ({@code service.auth}, {@code service.auth.passwordreset}, and {@code service.account}) that
 * need to issue, rotate, or revoke sessions as part of their own workflows. {@code
 * AuthSessionFactory} stays package-private: nothing outside this package constructs a session
 * directly.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity.internal.service.auth.session;
