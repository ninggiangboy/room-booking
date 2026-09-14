/**
 * Whether a host may sell on the platform and where their payouts may legally go.
 *
 * <p>KYC/KYB, screening, tax identity, licensing, and payout-destination eligibility — decided by
 * different evidence, on a different timeline, than {@code identity}'s question of who someone is.
 * This is a closed Spring Modulith application module: everything under {@code internal} is
 * invisible to every other module. {@link dev.ngb.backend.hostverification.HostOnboardingService}
 * and its two request/response records are the only legitimate cross-module dependency —
 * {@code identity}'s {@code UserController} calls it directly today. {@code HostProfileFactory}
 * sits at this same root package but keeps its package-private modifier, so it remains invisible
 * outside this module despite not living under {@code internal}. See
 * {@code docs/modules/hostverification.md} for the profile/case/eligibility cluster map and why the
 * factory could not stay under {@code internal.service.host} once its caller was promoted here.</p>
 */
@org.jspecify.annotations.NullMarked
package dev.ngb.backend.hostverification;
