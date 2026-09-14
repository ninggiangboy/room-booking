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
 * One affiliate agreement and the terms it pays under.
 *
 * <p>Frozen once active, because a commission rate that can be edited after bookings were
 * attributed cannot answer what the partner is owed.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("affiliate_partners")
public class AffiliatePartner {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The affiliate terms this agreement runs under. */
    private UUID growthProgramVersionId;
    /** Stable key naming the partner, unique across the platform. */
    private String partnerKey;
    /** Registered name of the partner. */
    private String legalName;
    /** ISO 3166-1 alpha-2 country the partner is registered in. */
    private String countryCode;
    /** Company registration reference, held in its owning system. */
    private @Nullable String registrationReference;
    /** Tax documentation reference, held in its owning system. */
    private @Nullable String taxFormReference;
    /** Share of the commissionable base the partner is paid. */
    private BigDecimal commissionPercent;
    /** ISO 4217 alphabetic code commissions are denominated in. */
    private String commissionCurrency;
    /** Which click gets the credit when there is more than one. */
    private AffiliateAttributionModel attributionModel;
    /** How long, in days, a click may still claim a booking. */
    private int attributionWindowDays;
    /** How long commission is held before payment, so a cancellation can reverse it. */
    private int payoutHoldDays;
    /** Reference to where commission is paid, held in its owning system. */
    private @Nullable String payoutDestinationRef;
    /** Where the agreement stands. */
    private AffiliatePartnerStatus status;
    /** Approved reason code recording why the agreement was suspended. */
    private @Nullable String suspensionReason;
    /** UTC instant the agreement began. */
    private @Nullable Instant activatedAt;
    /** UTC instant the agreement ended. */
    private @Nullable Instant terminatedAt;
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
