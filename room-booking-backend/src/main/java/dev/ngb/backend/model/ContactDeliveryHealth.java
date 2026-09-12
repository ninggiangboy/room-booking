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
 * Whether a destination still accepts what the platform sends it.
 *
 * <p>Keyed to the identity domain's contact row and the version of it that was observed, so
 * correcting a mistyped address starts a fresh history instead of inheriting the old one's
 * suppression. Suppression must name a reason and a source, because it is what will stop a future
 * send that somebody is waiting for.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("contact_delivery_health")
public class ContactDeliveryHealth {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Identity-owned contact row this health record is about. */
    private UUID contactChannelId;
    /** Version of that contact row these observations belong to. */
    private long contactChannelVersion;
    /** Channel the health record covers. */
    private NotificationChannel channel;
    /** Whether the destination is still worth sending to. */
    private ContactHealthStatus status;
    /** Permanent failures observed. */
    private int hardBounceCount;
    /** Temporary failures observed. */
    private int softBounceCount;
    /** Spam complaints observed. */
    private int complaintCount;
    /** When something last arrived. */
    private @Nullable Instant lastSuccessAt;
    /** When the last bounce arrived. */
    private @Nullable Instant lastBounceAt;
    /** When the last complaint arrived. */
    private @Nullable Instant lastComplaintAt;
    /** When sending was stopped. */
    private @Nullable Instant suppressedAt;
    /** Why it was stopped. */
    private @Nullable String suppressionReason;
    /** Who or what stopped it. */
    private @Nullable SuppressionSource suppressionSource;
    /** Whether a person still has to look at it. */
    private ContactHealthReviewState reviewState;
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
     * Whether a notice may be routed to this destination.
     *
     * @return {@code true} unless the destination is suppressed or unroutable
     */
    public boolean isRoutable() {
        return status == ContactHealthStatus.HEALTHY || status == ContactHealthStatus.DEGRADED;
    }
}
