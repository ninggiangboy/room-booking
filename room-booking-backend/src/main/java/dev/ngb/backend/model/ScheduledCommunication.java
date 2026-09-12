package dev.ngb.backend.model;

import java.time.Instant;
import java.time.LocalTime;
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
 * A reminder that has not happened yet.
 *
 * <p>The job names the fact it was derived from and the version of that fact, so a modified or
 * cancelled booking supersedes it rather than leaving a worker able to send arrival instructions for
 * a stay that moved. At most one live job exists per supersession key. The rule is local wall time in
 * a named zone, resolved once into an instant: both are kept, because the instant is what a worker
 * polls and the wall time is what the rule meant.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("scheduled_communications")
public class ScheduledCommunication {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Which schedule rule produced this job. */
    private String scheduleKey;
    /** Identity a replacement supersedes; at most one live job holds it. */
    private String supersessionKey;
    /** Which domain the anchoring fact belongs to. */
    private ScheduleAnchorDomain anchorDomain;
    /** The aggregate the job hangs from. */
    private UUID anchorAggregateId;
    /** Version of that aggregate, revalidated before firing. */
    private Integer anchorVersion;
    /** Booking the reminder concerns, where there is one. */
    private @Nullable UUID bookingId;
    /** Revision whose terms the reminder describes. */
    private @Nullable UUID bookingRevisionId;
    /** Who is to be reminded. */
    private UUID recipientAccountHolderId;
    /** What the reminder is for. */
    private String purposeCode;
    /** Policy version that will create the intent. */
    private UUID notificationPolicyId;
    /** The wall-time rule, such as one day before check-in at 09:00. */
    private String localRule;
    /** Local wall time the rule resolves to. */
    private LocalTime localTime;
    /** IANA zone that wall time is read in. */
    private String timeZone;
    /** Instant the worker polls for. */
    private Instant resolvedRunAt;
    /** After this, firing late would send misleading instructions. */
    private @Nullable Instant usefulnessExpiresAt;
    /** How far the job has got. */
    private ScheduledCommunicationState state;
    /** When a worker claimed it. */
    private @Nullable Instant claimedAt;
    /** Which worker holds the lease. */
    private @Nullable String claimedBy;
    /** When that lease lapses. */
    private @Nullable Instant leaseExpiresAt;
    /** Intent it created, required once fired. */
    private @Nullable UUID createdIntentId;
    /** Why it was discarded or expired. */
    private @Nullable String discardReason;
    /** Replacement job, required once superseded. */
    private @Nullable UUID supersededById;
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
     * Whether this job may still fire.
     *
     * @return {@code true} while it is scheduled or claimed
     */
    public boolean isLive() {
        return state == ScheduledCommunicationState.SCHEDULED
                || state == ScheduledCommunicationState.CLAIMED;
    }
    /**
     * Whether firing at the given instant would still be useful.
     *
     * @param at instant being evaluated
     * @return {@code false} once the usefulness expiry has passed
     */
    public boolean isUsefulAt(Instant at) {
        return usefulnessExpiresAt == null || usefulnessExpiresAt.isAfter(at);
    }
}
