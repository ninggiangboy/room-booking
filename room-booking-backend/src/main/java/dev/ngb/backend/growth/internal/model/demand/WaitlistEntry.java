package dev.ngb.backend.growth.internal.model.demand;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;
import dev.ngb.backend.platform.StayRange;

import dev.ngb.backend.platform.StayRange;


/**
 * One guest waiting for something that is not currently available.
 *
 * <p>An entry only reaches an offer with a quote holding the inventory behind it: telling
 * somebody a room opened up without holding one is fabricated scarcity with their calendar
 * attached.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("waitlist_entries")
public class WaitlistEntry {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The guest waiting. */
    private UUID accountHolderId;
    /** The listing waited for. */
    private @Nullable UUID listingId;
    /** The accommodation type waited for. */
    private @Nullable UUID accommodationTypeId;
    /** The area waited for. */
    private @Nullable UUID geoAreaId;
    /** Half-open stay range the guest is waiting for. */
    private StayRange stayRange;
    /** How many adults would stay. */
    private short adultCount;
    /** How many children would stay. */
    private short childCount;
    /** How many units are wanted. */
    private short unitQuantity;
    /** The most the guest will pay a night, in integer minor units. */
    private @Nullable Long maximumNightlyMinor;
    /** ISO 4217 alphabetic code that ceiling is denominated in. */
    private @Nullable String currency;
    /** The consent that permits telling the guest when something opens. */
    private UUID communicationConsentId;
    /** Where the guest stands in the queue. */
    private @Nullable Integer queuePosition;
    /** Where the entry stands. */
    private WaitlistEntryState state;
    /** UTC instant the guest joined the queue. */
    private Instant joinedAt;
    /** UTC instant a room was offered. */
    private @Nullable Instant offeredAt;
    /** UTC instant the offer lapses, which is when the hold behind it does. */
    private @Nullable Instant offerExpiresAt;
    /** The quote holding the inventory being offered; there is no offer without one. */
    private @Nullable UUID offerQuoteId;
    /** Migration 024 notification that carried the offer. */
    private @Nullable UUID offerIntentId;
    /** The booking the offer became. */
    private @Nullable UUID convertedBookingId;
    /** Approved reason code recording why the guest left the queue. */
    private @Nullable String withdrawalReason;
    /** UTC instant the entry stops waiting, so a queue never grows without end. */
    private Instant expiresAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;
}
