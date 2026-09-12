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
 * What a guest owes, fixed when the booking's terms were accepted.
 *
 * <p>The client never supplies an amount. It supplies a checkout, and the amount is read from
 * here. Re-routing to another provider, a second attempt, or a provider migration never changes
 * this row's identity — an obligation is not a provider object.</p>
 *
 * <p>The derived totals carry the two ceilings that hold real money: captured plus reserved can
 * never exceed {@link #amountMinor}, and refunded plus reserved can never exceed what was
 * captured. Both are check constraints, so a wrong lock in application code still cannot
 * overdraw.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("collection_obligations")
public class CollectionObligation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Reference shown to the guest and quoted by support. */
    private String publicId;
    /** Booking this obligation belongs to. */
    private UUID bookingId;
    /** Checkout attempt that created it, when one did. */
    private @Nullable UUID checkoutId;
    /** Immutable booking money snapshot the amount was copied from. */
    private UUID financialSnapshotId;
    /** Why this amount is owed. */
    private CollectionPurpose purpose;
    /** Account that owes the money. */
    private UUID debtorAccountHolderId;
    /** Merchant entity collecting it. */
    private UUID legalEntityId;
    /** Market whose rules govern the collection. */
    private String marketCode;
    /** ISO 4217 code the whole obligation is denominated in. */
    private String currency;
    /** Amount owed, in minor units. */
    private long amountMinor;
    /** Minor units successfully collected. */
    private long capturedAmountMinor;
    /** Minor units held by in-flight collection operations. */
    private long captureReservedMinor;
    /** Minor units successfully returned. */
    private long refundedAmountMinor;
    /** Minor units held by in-flight refund operations. */
    private long refundReservedMinor;
    /** Minor units currently contested by a provider case. */
    private long disputedAmountMinor;
    /** Progress of the collection. */
    private CollectionObligationState state;
    /** How the money is meant to be collected. */
    private CapturePolicy capturePolicy;
    /** Version of the policy that chose it. */
    private String capturePolicyVersion;
    /**
     * Method families policy permitted when the obligation was created.
     *
     * <p>Snapshotted rather than joined, so that a later policy change cannot retroactively make a
     * completed collection look unauthorised.</p>
     */
    private JsonDocument allowedMethodFamilies;
    /** Market policy bundle the terms came from. */
    private @Nullable UUID policyBundleId;
    /** UTC instant the amount falls due. */
    private Instant dueAt;
    /** UTC instant after which it can no longer be satisfied. */
    private @Nullable Instant expiresAt;
    /**
     * UTC instant collection completed.
     *
     * <p>A later refund does not clear this. Forgetting that the money was ever collected is exactly
     * the fact a refund argument turns on.</p>
     */
    private @Nullable Instant settledAt;
    /** UTC instant it was closed unsatisfied. */
    private @Nullable Instant cancelledAt;
    /** Why it was closed unsatisfied. */
    private @Nullable String cancellationReason;
    /** Identifier following the whole booking saga. */
    private String correlationId;
    /** Kind of actor that created it. */
    private BookingActorType createdByActorType;
    /** Identity of that actor, when it has one. */
    private @Nullable UUID createdByActorId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Minor units still collectable, after successful captures and in-flight reservations.
     *
     * @return remaining amount in minor units, never negative
     */
    public long remainingCollectableMinor() {
        return amountMinor - capturedAmountMinor - captureReservedMinor;
    }

    /**
     * Minor units still returnable, after successful refunds and in-flight reservations.
     *
     * <p>This is the quantity the transactional refund limit is computed from. It is read under
     * a lock on this row, which is what stops two concurrent partial refunds from exceeding the
     * money that was actually captured.</p>
     *
     * @return refundable amount in minor units, never negative
     */
    public long refundableMinor() {
        return capturedAmountMinor - refundedAmountMinor - refundReservedMinor;
    }

    /**
     * Whether the obligation can still accept a new collection attempt.
     *
     * @return {@code true} while it is open to being satisfied
     */
    public boolean isCollectable() {
        return switch (state) {
            case OPEN, ACTION_REQUIRED, PROCESSING, AUTHORIZED, PARTIALLY_PAID -> true;
            case PAID, PARTIALLY_REFUNDED, REFUNDED, VOIDED, CANCELLED, EXPIRED -> false;
        };
    }
}
