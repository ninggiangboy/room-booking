/**
 * Multi-factor enrollment (TOTP) and step-up proof issuance for a sensitive action.
 *
 * <p>{@code MfaService} is {@code public} because {@code internal.web.MfaController} calls it
 * directly, and because {@link #consumeStepUpProof} is the primitive another workflow (a future
 * change to {@code UserAccountService.changePassword}, for instance) calls before proceeding with
 * a sensitive action — nothing in this codebase requires step-up yet; this package builds the
 * mechanism a caller can adopt without revisiting it, the same way {@code CapabilityGrantService}'s
 * revoke cascade was built before {@code OrganizationService} became its first caller.
 * {@code TotpCredentialFactory} stays package-private: nothing outside this package constructs a
 * TOTP credential row directly.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity.internal.service.mfa;
