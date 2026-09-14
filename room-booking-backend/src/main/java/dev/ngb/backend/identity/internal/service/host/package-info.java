/**
 * Promotes an active guest account to a host account.
 *
 * <p>This is the codebase's legacy host-onboarding workflow, running against {@code host_profiles}
 * (migration {@code 001}) rather than {@code hostverification}'s target-model
 * {@code host_legal_profiles} (migration {@code 015}) -- the same duality
 * {@code docs/modules/identity.md} already documents for {@code users} versus
 * {@code account_holders}. It lives here, not in {@code hostverification}, because it reads and
 * writes {@code identity}'s own {@code User} and {@code UserRole} rows directly; moving the
 * service without moving the tables it operates on would have turned that direct access into an
 * illegal reach into another module's internals. {@link
 * dev.ngb.backend.identity.internal.service.host.HostProfileFactory} stays package-private beside
 * its only caller.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity.internal.service.host;
