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
 * Whether one capability, payment method, or payout rail may be used in a market, and when.
 *
 * <p>Availability is granted explicitly and effective-dated. A market with no row for a capability
 * does not have it: absence means disabled, so an unconfigured market cannot inherit a capability by
 * accident.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("market_capabilities")
public class MarketCapability {

    /** Primary key of the availability record. */
    @Id
    private @Nullable UUID id;
    /** Market the availability applies to. */
    private UUID marketId;
    /** Capability being granted, such as {@code INSTANT_BOOK} or {@code HOST_PAYOUT}. */
    private String capability;
    /** Payment method the grant narrows to, where the capability is method-specific. */
    private @Nullable String methodKey;
    /** Payout rail the grant narrows to, where the capability is rail-specific. */
    private @Nullable String railKey;
    /** Whether the capability is disabled, piloting, generally available, or withdrawn. */
    private CapabilityAvailability availabilityState;
    /** Deterministic conditions selecting which traffic the grant covers during a pilot. */
    private @Nullable JsonDocument eligibilityRule;
    /** UTC instant from which the grant applies, inclusive. */
    private Instant effectiveFrom;
    /** UTC instant from which it stops applying, exclusive; {@code null} while current. */
    private @Nullable Instant effectiveUntil;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;
}
