package dev.ngb.backend.model;

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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One replacement stay offered to a guest who must be moved.
 *
 * <p>Declined offers are kept. Three declines is evidence about the offers, not about the guest, and a
 * case that settled on the fourth option needs the first three to stay explainable.</p>
 *
 * <p>An offer is either a listing on the platform or a described alternative off it, never neither. At
 * most one offer per case may be accepted: a guest cannot be relocated into two stays.</p>
 *
 * <p>Offers are written once and answered once, so the row carries no optimistic lock.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("relocation_offers")
public class RelocationOffer {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Case the offer belongs to. */
    private UUID relocationCaseId;
    /** Position within the case. */
    private short sequenceNumber;
    /** Supply on the platform, where the offer is one. */
    private @Nullable UUID listingId;
    /** The alternative, where it is off the platform. */
    private @Nullable String externalDescription;
    /** Half-open range the offer covers. */
    private StayRange stayRange;
    /** How far from the original property. */
    private @Nullable Integer distanceMetres;
    /** Whether it satisfies everything the guest said it must. */
    private boolean meetsHardConstraints;
    /** ISO 4217 code. */
    private String currency;
    /** What the replacement costs. */
    private long offeredAmountMinor;
    /** What the guest would pay towards it. */
    private long guestContributionMinor;
    /** What the platform would absorb. */
    private long platformCostMinor;
    /** What the original host would absorb. */
    private long hostCostMinor;
    /** What happened to the offer. */
    private RelocationOfferState state;
    /** When the guest was shown it. */
    private Instant presentedAt;
    /** When they answered. */
    private @Nullable Instant respondedAt;
    /** When an unanswered offer lapses. */
    private @Nullable Instant expiresAt;
    /** Why the guest refused. */
    private @Nullable String declineReason;
    /** Booking created from it. Only ever on an accepted offer. */
    private @Nullable UUID resultingBookingId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether the offer is the one the case settled on.
     *
     * @return {@code true} when the guest accepted it
     */
    public boolean wasAccepted() {
        return state == RelocationOfferState.ACCEPTED;
    }
}
