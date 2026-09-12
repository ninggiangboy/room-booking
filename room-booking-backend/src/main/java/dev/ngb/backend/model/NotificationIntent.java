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
 * The decision to tell one person one thing once.
 *
 * <p>One committed event, one recipient, one purpose, one policy version means one intent -- a unique
 * key, not a convention. Everything below it may be retried freely, because the intent is what
 * carries the identity. Its identity columns and input hash are frozen by trigger, so an existing
 * intent cannot be quietly repointed to achieve a duplicate by another route.</p>
 *
 * <p>The destination itself stays in the identity boundary: what is kept here is which contact row
 * was resolved and at which version.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("notification_intents")
public class NotificationIntent {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Committed domain event this intent was derived from. */
    private UUID sourceEventId;
    /** Type of that event. */
    private String sourceEventType;
    /** Version of the aggregate the event reported. */
    private @Nullable Integer sourceAggregateVersion;
    /** Who is to be told. */
    private UUID recipientAccountHolderId;
    /** Identity-owned contact row resolved as the destination. */
    private @Nullable UUID contactChannelId;
    /** Version of that contact row at resolution time. */
    private @Nullable Long contactChannelVersion;
    /** What the notice is for. */
    private String purposeCode;
    /** Whether consent and quiet hours apply. */
    private NotificationClassification classification;
    /** Policy version that created this intent. */
    private UUID notificationPolicyId;
    /** Template family to render with. */
    private String templateFamily;
    /** Locale to render in. */
    private String locale;
    /** When it may be sent. */
    private Instant scheduledFor;
    /** When it stops being worth sending. */
    private @Nullable Instant expiresAt;
    /** The domain deadline the notice is about, so copy never implies delivery moved it. */
    private @Nullable Instant domainDeadlineAt;
    /** How far the intent has progressed. */
    private NotificationIntentState state;
    /** Why it was suppressed, required in that state. */
    private @Nullable String suppressionReason;
    /** Replacement intent, required once superseded. */
    private @Nullable UUID supersededByIntentId;
    /** Why no route succeeded, required once failed. */
    private @Nullable String failureReason;
    /** SHA-256 of the facts the intent was built from; never rewritten. */
    private String inputHash;
    /** Correlation handle shared with the originating command. */
    private @Nullable UUID correlationId;
    /** When a worker claimed it. */
    private @Nullable Instant claimedAt;
    /** Which worker holds the lease. */
    private @Nullable String claimedBy;
    /** When that lease lapses and the intent becomes claimable again. */
    private @Nullable Instant leaseExpiresAt;
    /** When it reached a terminal state. */
    private @Nullable Instant completedAt;
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
     * Whether a worker may still act on this intent.
     *
     * @return {@code true} while it is waiting, ready or being dispatched
     */
    public boolean isLive() {
        return state == NotificationIntentState.PENDING
                || state == NotificationIntentState.SCHEDULED
                || state == NotificationIntentState.READY
                || state == NotificationIntentState.DISPATCHING;
    }
    /**
     * Whether the notice is still worth sending at the given instant.
     *
     * @param at instant being evaluated
     * @return {@code true} when no expiry has passed
     */
    public boolean isUsefulAt(Instant at) {
        return expiresAt == null || expiresAt.isAfter(at);
    }
}
