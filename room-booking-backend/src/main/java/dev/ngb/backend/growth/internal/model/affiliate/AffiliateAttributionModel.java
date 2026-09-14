package dev.ngb.backend.growth.internal.model.affiliate;

/**
 * Which click is credited when a booking follows more than one.
 */
public enum AffiliateAttributionModel {

    /** The most recent click inside the window takes the credit. */
    LAST_CLICK,

    /** The earliest click inside the window takes the credit. */
    FIRST_CLICK
}
