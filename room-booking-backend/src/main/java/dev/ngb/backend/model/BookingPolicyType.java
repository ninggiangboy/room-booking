package dev.ngb.backend.model;

/**
 * Which policy a guest accepted as part of a booking.
 *
 * <p>Each is recorded separately with its own version, because they change on different schedules
 * and a dispute is usually about exactly one of them. Proving a guest accepted "the terms" is worth
 * very little; proving which cancellation policy version they accepted decides a refund.</p>
 */
public enum BookingPolicyType {
    /** The host's rules for behaviour at the property. */
    HOUSE_RULES,
    /** The cancellation terms the refund entitlement is computed from. */
    CANCELLATION,
    /** The platform's terms of service. */
    TERMS_OF_SERVICE,
    /** The privacy notice in force at booking time. */
    PRIVACY,
    /** Safety disclosures for the property. */
    SAFETY,
    /** The standards expected of guests. */
    GUEST_STANDARDS,
    /** Liability terms for damage to the property. */
    DAMAGE_POLICY
}
