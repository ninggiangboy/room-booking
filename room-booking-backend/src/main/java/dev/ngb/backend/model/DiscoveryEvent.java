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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One validated behavioural fact, append-only and expiring on a date it carries itself.
 *
 * <p>These rows help attribution; they never redefine what a booking or a review says. Notice what is
 * absent: no address, no coordinate, no token, no payment detail, no IP address, no raw user-agent
 * string. Those fields do not exist so that no future job can be tempted to fill them.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("discovery_events")
public class DiscoveryEvent {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Client-issued event identity; a retry carrying the same key is discarded rather than counted twice. */
    private String eventKey;
    /** Which behavioural event this row records. */
    private DiscoveryEventType eventType;
    /** Which version of the event payload contract the producer wrote. */
    private short schemaVersion;
    /** UTC instant the behaviour happened, as the producer observed it. */
    private Instant occurredAt;
    /** UTC instant ingestion accepted it; out-of-order delivery is tolerated. */
    private Instant receivedAt;
    /** The search request this row belongs to. */
    private @Nullable UUID searchRequestId;
    /** Analytical pseudonym for the session, kept separate from any operational session identifier. */
    private String sessionPseudonym;
    /** The account holder this row belongs to. */
    private @Nullable UUID accountHolderId;
    /** The listing this row belongs to. */
    private @Nullable UUID listingId;
    /** One-based rank the listing occupied, only meaningful beside a search request. */
    private @Nullable Short position;
    /** One-based page the listing appeared on. */
    private @Nullable Short pageNumber;
    /** Whether the listing was rendered where a guest could actually see it; an impression requires this. */
    private @Nullable Boolean visibleRender;
    /** Milliseconds of dwell, capped, because an open browser tab is not evidence of interest. */
    private @Nullable Integer dwellMs;
    /** Where in the product the interaction happened. */
    private DiscoverySurface interactionSurface;
    /** What ingestion did with the event. */
    private DiscoveryIngestState ingestState;
    /** Why the event was rejected or quarantined; required whenever it was. */
    private @Nullable String rejectionReason;
    /** How long the event may be kept. */
    private DiscoveryRetentionClass retentionClass;
    /** UTC instant after which the event must be deleted; every event carries its own deadline. */
    private Instant expiresAt;
    /** Bounded request context snapshot. Never an address, a coordinate, a token, or a payment detail. */
    private @Nullable String context;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
