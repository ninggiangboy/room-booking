/**
 * Whether a host may sell on the platform and where their payouts may legally go.
 *
 * <p>KYC/KYB, screening, tax identity, licensing, and payout-destination eligibility — decided by
 * different evidence, on a different timeline, than {@code identity}'s question of who someone is.
 * Schema-only as of this migration: the legacy host-onboarding workflow that creates
 * {@code host_profiles} rows and grants the {@code HOST} role lives in {@code identity}, not here,
 * because it operates on {@code identity}'s own tables — see
 * {@code identity.internal.service.host}'s package Javadoc. This is a closed Spring Modulith
 * application module; everything under {@code internal} is invisible to every other module. See
 * {@code docs/modules/hostverification.md} for the profile/case/eligibility cluster map.</p>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"platform"})
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.hostverification;
