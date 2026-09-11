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
 * A market whose rules govern supply, pricing, contracting, payment, and payout.
 *
 * <p>The market is the authority for a booking's currency, time zone, and applicable policy. It is
 * resolved from the property, never from the guest's locale, phone number, or IP address: a client
 * cannot choose which country's consumer-protection rules apply to a stay.</p>
 *
 * <p>Supported currencies, locales, and zones live in their own tables rather than in array columns,
 * because eligibility and rendering filter on them.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("markets")
public class Market {

    /** Primary key of the market. */
    @Id
    private @Nullable UUID id;
    /** Stable ISO 3166-1 alpha-2 code, such as {@code VN}. */
    private String marketCode;
    /** Operator-facing name of the market. */
    private String displayName;
    /** ISO 4217 currency contracts in this market are denominated in. */
    private String defaultCurrency;
    /** BCP 47 presentation default, such as {@code vi-VN}; never legal authority. */
    private String defaultLocale;
    /** Full IANA {@code Region/City} zone; aliases such as {@code UTC} are rejected. */
    private String defaultTimeZone;
    /** Whether the market may be used by domain decisions. */
    private ConfigurationLifecycle lifecycleState;
    /** Increments on each activation, so a re-activation is distinguishable from the first. */
    private int activationVersion;
    /** UTC instant the market was first activated; {@code null} while still a draft. */
    private @Nullable Instant activatedAt;
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
     * Reports whether domain decisions may resolve configuration from this market.
     *
     * @return {@code true} only when the market is active
     */
    public boolean isUsable() {
        return lifecycleState == ConfigurationLifecycle.ACTIVE;
    }
}
