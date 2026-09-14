package dev.ngb.backend.trust.internal.model.signal;

import dev.ngb.backend.supply.internal.model.listing.Listing;
import dev.ngb.backend.pricing.internal.model.promotion.Promotion;
import dev.ngb.backend.booking.internal.model.contract.Booking;

import dev.ngb.backend.booking.internal.model.contract.Booking;
import dev.ngb.backend.pricing.internal.model.promotion.Promotion;
import dev.ngb.backend.supply.internal.model.listing.Listing;

/**
 * Where an observation came from.
 *
 * <p>Part of the deduplication identity together with the provider account and the source record
 * identifier, so a redelivered fact cannot increment a velocity counter twice.</p>
 */
public enum RiskSignalSourceDomain {
    /** Accounts, sessions and authentication. */
    IDENTITY,
    /** Listing content and media. */
    LISTING,
    /** Availability and calendar. */
    INVENTORY,
    /** Booking lifecycle facts. */
    BOOKING,
    /** Payment attempts, operations and provider observations. */
    PAYMENT,
    /** Ledger and payout facts. */
    FINANCE,
    /** Conversations and delivery. */
    MESSAGING,
    /** Reviews, votes and reports. */
    REVIEW,
    /** Promotion grants and redemptions. */
    PROMOTION,
    /** Access, tasks and incidents. */
    STAY_OPERATIONS,
    /** Support cases. */
    SUPPORT,
    /** An external vendor observation. */
    PROVIDER,
    /** Platform-derived telemetry. */
    PLATFORM,
    /** Something a person alleged. */
    USER_REPORT;
}
