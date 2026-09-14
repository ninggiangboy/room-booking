/**
 * The account holder, its credentials, its sessions, and what it is permitted to do.
 *
 * <p>Registration, login, email verification, password reset, and refresh-token rotation are the
 * only substantial live application workflows in this codebase; all of them live here. This is a
 * closed Spring Modulith application module: everything under {@code internal} is invisible to
 * every other module, and only the types at this root — {@link
 * dev.ngb.backend.identity.AccessTokenService}, {@link dev.ngb.backend.identity.internal.service.user.UserFinder}, and the
 * two published events — are a legitimate cross-module dependency. See {@code docs/modules/identity.md}
 * for the full account/credential/session/capability cluster map, the open {@code users} versus
 * {@code account_holders} question this migration deliberately leaves unresolved, and why
 * {@code AccessTokenService} in particular is promoted here rather than kept internal.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"config", "platform", "platform :: exception.base", "platform :: util"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity;
