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
 * One standing request to be told when a price falls or a room opens.
 *
 * <p>A price-drop alert compares against a price that was actually observed, a last-units alert
 * names how few is few, and every alert carries an expiry.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("demand_alerts")
public class DemandAlert {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The guest who asked to be told. */
    private UUID accountHolderId;
    /** What the guest wants to hear about. */
    private DemandAlertKind alertKind;
    /** The listing being watched. */
    private @Nullable UUID listingId;
    /** The saved search being watched, which must be the same guest’s. */
    private @Nullable UUID savedSearchId;
    /** Half-open stay range the alert covers. */
    private @Nullable StayRange stayRange;
    /** How far the price must fall, as a share of the baseline. */
    private @Nullable BigDecimal thresholdPercent;
    /** How far the price must fall, in integer minor units. */
    private @Nullable Long thresholdAmountMinor;
    /** ISO 4217 alphabetic code the amount threshold is denominated in. */
    private @Nullable String thresholdCurrency;
    /** How few units count as few, so scarcity is a number and not a phrase. */
    private @Nullable Short remainingUnitsThreshold;
    /** The price actually observed when the alert was set. */
    private @Nullable Long baselineAmountMinor;
    /** UTC instant that price was observed. */
    private @Nullable Instant baselineCapturedAt;
    /** The consent that permits the alert. */
    private UUID communicationConsentId;
    /** Channel the alert is sent on. */
    private NotificationChannel notifyChannel;
    /** Where the alert stands. */
    private DemandAlertState state;
    /** How many times the alert has fired. */
    private int triggerCount;
    /** UTC instant it last fired. */
    private @Nullable Instant lastTriggeredAt;
    /** Migration 024 notification that carried the most recent alert. */
    private @Nullable UUID triggeredIntentId;
    /** Approved reason code recording why the guest turned it off. */
    private @Nullable String cancellationReason;
    /** UTC instant the alert stops watching, so it never runs forever. */
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
