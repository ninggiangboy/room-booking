/**
 * The account holder, its credentials, its sessions, and what it is permitted to do.
 *
 * <p>Registration, login, email verification, password reset, and refresh-token rotation are the
 * only substantial live application workflows in this codebase; all of them live here. This is a
 * closed Spring Modulith application module: everything under {@code internal} is invisible to
 * every other module, and only the types at this root — {@link
 * dev.ngb.backend.identity.AccessTokenService}, {@link dev.ngb.backend.identity.IdentityFacts},
 * and the two published events — are a legitimate cross-module dependency. See {@code
 * docs/modules/identity.md} for the full account/credential/session/capability cluster map. The
 * legacy {@code users} table was retired by migration {@code 037}: {@link
 * dev.ngb.backend.identity.internal.model.account.AccountHolder} is now the single principal
 * root, which is why {@code AccessTokenService} and {@code IdentityFacts} are promoted here
 * rather than kept internal. {@code market} is also a declared dependency: {@link
 * dev.ngb.backend.identity.internal.service.account.AdminAccountService#resolveMarket} calls
 * {@code market}'s {@code MarketLookup} to validate an operator-supplied market code before
 * recording it on a holder.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"config", "market", "platform", "platform :: exception.base", "platform :: util"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity;
