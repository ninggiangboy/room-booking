package dev.ngb.backend.trust.internal.model.review;

import dev.ngb.backend.supply.internal.model.listing.Listing;
import dev.ngb.backend.messaging.internal.model.delivery.Message;

import dev.ngb.backend.messaging.internal.model.delivery.Message;
import dev.ngb.backend.supply.internal.model.listing.Listing;

/**
 * What a reviewer must be qualified in to take work from a queue.
 */
public enum ReviewerSkill {
    /** Account takeover and credential abuse. */
    ACCOUNT_SECURITY,
    /** Listing content and publication risk. */
    LISTING_MODERATION,
    /** Payment and instrument abuse. */
    PAYMENT_FRAUD,
    /** Payout diversion and financial crime. */
    PAYOUT_RISK,
    /** Message, review and profile content. */
    CONTENT_MODERATION,
    /** Urgent safety matters. */
    SEVERE_SAFETY,
    /** Sampling and reviewer quality. */
    QUALITY_ASSURANCE,
    /** Hearing appeals. */
    APPEALS;
}
