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
 * An external challenge to a captured payment.
 *
 * <p>A chargeback is not a refund, and it does not make the original capture unsuccessful. It is a
 * separate fact hanging off the capture, with its own deadline, its own outcome, and its own
 * money.</p>
 *
 * <p>Missing the response deadline loses the case by default, so {@link #respondBy} is a column
 * workers page on rather than something buried in a provider payload.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("payment_disputes")
public class PaymentDispute {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Merchant account the case was opened against. */
    private UUID providerAccountId;
    /** Provider identifier for the case. */
    private String providerDisputeId;
    /** Reference the provider quotes in correspondence. */
    private @Nullable String providerCaseReference;
    /** Capture being contested. */
    private @Nullable UUID captureOperationId;
    /** Obligation the contested money belongs to. */
    private @Nullable UUID obligationId;
    /** Booking the contested money belongs to. */
    private @Nullable UUID bookingId;
    /** Kind of challenge. */
    private DisputeType disputeType;
    /** ISO 4217 code of the contested amount. */
    private String currency;
    /** Minor units being contested. */
    private long disputedAmountMinor;
    /** Minor units the provider charged for the case. */
    private long feeAmountMinor;
    /** Minor units returned to the platform after the outcome. */
    private long recoveredAmountMinor;
    /** Normalised reason the case was raised. */
    private String reasonCategory;
    /** The provider's own reason code. */
    private @Nullable String providerReasonCode;
    /** Progress of the case; the terminal values are the outcome. */
    private DisputeStatus status;
    /** UTC instant the case was opened. */
    private Instant openedAt;
    /** UTC instant a response is due; required once one is required. */
    private @Nullable Instant respondBy;
    /** UTC instant representment was sent. */
    private @Nullable Instant submittedAt;
    /** UTC instant it reached a terminal status. */
    private @Nullable Instant resolvedAt;
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
     * Whether the case is still live.
     *
     * @return {@code true} until it reaches a terminal status
     */
    public boolean isOpen() {
        return switch (status) {
            case INQUIRY_OR_RETRIEVAL, ACTION_REQUIRED, EVIDENCE_SUBMITTED, UNDER_REVIEW -> true;
            case WON, LOST, ACCEPTED, EXPIRED -> false;
        };
    }
}
