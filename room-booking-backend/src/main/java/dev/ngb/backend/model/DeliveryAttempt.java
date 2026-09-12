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
 * One crossing to one provider for one intent.
 *
 * <p>The fence is the same one payment orchestration uses: nothing may claim a provider outcome
 * without a submission instant, and a failure must say which kind it was -- so an unanswered send can
 * only be {@code UNKNOWN}, and an unknown one is queried or reconciled before a replacement goes
 * out. State is the reduction of the attempt's observations by evidence precedence.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("delivery_attempts")
public class DeliveryAttempt {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Intent being delivered. */
    private UUID notificationIntentId;
    /** Render whose content was sent. */
    private @Nullable UUID notificationRenderId;
    /** Channel used. */
    private NotificationChannel channel;
    /** Route chosen by channel policy. */
    private String routeKey;
    /** Which attempt this is on that channel, starting at one. */
    private short attemptNumber;
    /** Provider account the attempt was submitted through. */
    private @Nullable UUID providerAccountId;
    /** Stable key given to the provider so a retry cannot duplicate a send. */
    private @Nullable String providerIdempotencyKey;
    /** The provider's own handle for the send, unique within its account. */
    private @Nullable String providerReference;
    /** How far this crossing has got. */
    private DeliveryAttemptState state;
    /** Why it failed, required once failed. */
    private @Nullable DeliveryFailureCategory failureCategory;
    /** Provider detail behind that category. */
    private @Nullable String failureDetail;
    /** When a worker claimed it. */
    private @Nullable Instant claimedAt;
    /** Which worker holds the lease. */
    private @Nullable String claimedBy;
    /** When that lease lapses. */
    private @Nullable Instant leaseExpiresAt;
    /** When it crossed to the provider. */
    private @Nullable Instant submittedAt;
    /** When the attempt reached a terminal outcome. */
    private @Nullable Instant completedAt;
    /** When a retry becomes due; only for outcomes that might still change. */
    private @Nullable Instant nextRetryAt;
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
     * Whether this attempt reached the destination as far as the provider reported.
     *
     * @return {@code true} for delivered attempts
     */
    public boolean isDelivered() {
        return state == DeliveryAttemptState.DELIVERED;
    }
    /**
     * Whether a replacement send may be issued.
     *
     * <p>An unknown submission may not: it is queried or reconciled first, because a duplicate notice
     * is a real cost to the recipient.</p>
     *
     * @return {@code true} only for a failure whose kind is worth retrying
     */
    public boolean isRetryable() {
        return state == DeliveryAttemptState.FAILED
                && failureCategory != DeliveryFailureCategory.INVALID_DESTINATION
                && failureCategory != DeliveryFailureCategory.SUPPRESSED;
    }
}
