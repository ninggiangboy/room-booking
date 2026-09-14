package dev.ngb.backend.support.internal.model.queue;

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

import dev.ngb.backend.support.internal.model.CaseSeverity;


/**
 * Where work waits, and what a person must hold to take it.
 *
 * <p>Capacity thresholds exist so overflow and escalation happen visibly instead of promises quietly
 * lengthening behind a growing backlog.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("support_queues")
public class SupportQueue {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the queue. */
    private String queueKey;
    /** Stable key naming the display name. */
    private String displayNameKey;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** The legal entity this row belongs to. */
    private @Nullable UUID legalEntityId;
    /** Where the status stands. */
    private SupportQueueStatus status;
    /** Case types. */
    private String[] caseTypes;
    /** Severity floor. */
    private CaseSeverity severityFloor;
    /** Which sensitivity scope this row carries. */
    private String[] sensitivityScope;
    /** Languages. */
    private String[] languages;
    /** Required skills. */
    private String[] requiredSkills;
    /** Reference to the business calendar, held in its owning system rather than copied here. */
    private String businessCalendarReference;
    /** IANA zone the civil deadlines are computed in. */
    private String timeZone;
    /** Concurrency limit. */
    private @Nullable Integer concurrencyLimit;
    /** Depth warning threshold. */
    private @Nullable Integer depthWarningThreshold;
    /** Depth at which work overflows rather than promises quietly lengthening. */
    private @Nullable Integer depthOverflowThreshold;
    /** Stable key naming the overflow queue. */
    private @Nullable String overflowQueueKey;
    /** Escalation route. */
    private String escalationRoute;
    /** Whether the queue can wake a human, which safety-critical work requires. */
    private boolean pagingRequired;
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
