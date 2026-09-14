/**
 * Password-reset token issuance and consumption, and the credential-rotation logic it shares with
 * an authenticated password change.
 *
 * <p>{@code PasswordCredentialRotator} is {@code public} rather than package-private: it is also
 * used from {@code service.account} ({@code UserAccountService}) for an authenticated password
 * change, which is the same credential-rotation operation a reset performs, just reached through a
 * different workflow.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity.internal.service.auth.passwordreset;
