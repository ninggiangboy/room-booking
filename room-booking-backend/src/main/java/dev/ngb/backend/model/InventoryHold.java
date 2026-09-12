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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A promise that nights stay reservable while a guest finishes paying.
 *
 * <p>Temporary by construction. {@link #maxExpiresAt} bounds how far extensions can push
 * {@link #expiresAt}, so a stalled checkout cannot keep inventory off the market indefinitely by
 * repeatedly asking for more time — the database refuses an extension past the ceiling.</p>
 *
 * <p>{@link #fencingToken} exists because expiry is a race. A sweeper deciding this hold has lapsed
 * and a checkout completing payment can act at the same instant; the token lets whichever writes
 * second notice that it is acting on a decision that has already been superseded, rather than
 * releasing nights a guest has just paid for.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("inventory_holds")
public class InventoryHold {

    /** Primary key of the hold. */
    @Id
    private @Nullable UUID id;
    /** Opaque identifier safe to show a client. */
    private String publicId;
    /** Accommodation type whose nights are held. */
    private UUID accommodationTypeId;
    /** Who the hold is for, where a principal is known. */
    private @Nullable UUID subjectId;
    /** Quote the hold was taken against. */
    private @Nullable UUID quoteId;
    /** Why the nights are held, which decides how long the hold may live. */
    private HoldPurpose purpose;
    /** Whether the hold still stands, and how it ended if not. */
    private HoldStatus status;
    /** How many of a pooled type are held. */
    private int quantity;
    /** Nights held, half-open. */
    private StayRange stayRange;
    /** UTC instant the hold currently lapses at. */
    private Instant expiresAt;
    /** Ceiling that extensions may never push {@link #expiresAt} past. */
    private Instant maxExpiresAt;
    /** How many times the hold has been extended. */
    private short extensionCount;
    /** UTC instant the hold became a booking. */
    private @Nullable Instant consumedAt;
    /** UTC instant the hold gave its nights back. */
    private @Nullable Instant releasedAt;
    /** Stable reason it was released. */
    private @Nullable String releaseReason;
    /** Monotonic token used to detect a superseded expiry decision. */
    private long fencingToken;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Reports whether the hold still guarantees its nights at the supplied instant.
     *
     * <p>Equality on the deadline means expired, matching the platform's deadline convention: a
     * guest arriving exactly at the expiry has run out of time.</p>
     *
     * @param instant the command's decision instant
     * @return {@code true} when the hold is active and has not lapsed
     */
    public boolean isHoldingAt(Instant instant) {
        return status == HoldStatus.ACTIVE && expiresAt.isAfter(instant);
    }

    /**
     * Reports whether the hold may be extended to a proposed new expiry.
     *
     * <p>An extension may never cross the ceiling set when the hold was created, which is what stops
     * an abandoned checkout from holding inventory forever.</p>
     *
     * @param proposedExpiry the expiry being requested
     * @return {@code true} when the hold is active and the proposal stays within its ceiling
     */
    public boolean canExtendTo(Instant proposedExpiry) {
        return status == HoldStatus.ACTIVE
                && proposedExpiry.isAfter(expiresAt)
                && !proposedExpiry.isAfter(maxExpiresAt);
    }
}
