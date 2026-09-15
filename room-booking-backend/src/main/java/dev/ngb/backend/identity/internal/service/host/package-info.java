/**
 * Promotes an active guest account to a host account.
 *
 * <p>The workflow issues exactly one thing: a {@code HOST} capability grant. Migration
 * {@code 037} dropped {@code host_profiles}, which used to live here and carried {@code bio},
 * {@code average_rating}, and {@code review_count} -- fields this module never owned per {@code
 * docs/features/identity-accounts-and-access.md} § Profile facts and their consumers. Public host
 * profile data belongs to whichever module eventually owns it, and host identity verification
 * already lives in {@code hostverification}'s target-model {@code host_legal_profiles} (migration
 * {@code 015}).</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.identity.internal.service.host;
