package dev.ngb.backend.model;

import java.math.BigDecimal;
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
 * Commission owed to a partner for one credited booking.
 *
 * <p>The amount follows from the agreement’s own rate and base, the payout hold runs before it
 * can be paid, and a commission that has already been paid is recovered through migration 022
 * rather than reversed here.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("affiliate_commissions")
public class AffiliateCommission {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The credited click, which earns commission once. */
    private UUID affiliateAttributionId;
    /** The partner being paid. */
    private UUID affiliatePartnerId;
    /** The booking the commission is owed on. */
    private UUID bookingId;
    /** The part of the booking commission is computed on, in integer minor units. */
    private long commissionableBaseMinor;
    /** The rate applied, which must be the agreement’s own. */
    private BigDecimal commissionPercent;
    /** Commission owed, in integer minor units, and equal to base times rate. */
    private long commissionMinor;
    /** ISO 4217 alphabetic code the commission is denominated in. */
    private String currency;
    /** Where the commission stands. */
    private AffiliateCommissionState state;
    /** UTC instant the hold ends and payment becomes possible. */
    private Instant maturesAt;
    /** UTC instant the commission was earned and posted. */
    private @Nullable Instant earnedAt;
    /** The posting that recorded it. */
    private @Nullable UUID ledgerTransactionId;
    /** UTC instant the commission was paid. */
    private @Nullable Instant paidAt;
    /** Migration 022 payout instruction that paid it. */
    private @Nullable UUID payoutInstructionId;
    /** UTC instant the commission was reversed. */
    private @Nullable Instant reversedAt;
    /** Approved reason code recording why it was reversed. */
    private @Nullable String reversalReason;
    /** The posting that reversed it. */
    private @Nullable UUID reversalTransactionId;
    /** Approved reason code recording why payment is being held back. */
    private @Nullable String withheldReason;
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
