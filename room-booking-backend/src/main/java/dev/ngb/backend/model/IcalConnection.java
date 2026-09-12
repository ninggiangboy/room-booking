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
 * A calendar feed exchanged with another platform.
 *
 * <p>External calendars are the main way a small host oversells: they list the same apartment here
 * and elsewhere, and whichever system learns about a booking last sells a night that is already gone.
 * Import cannot prevent that entirely — the other site may take a booking a minute before a sync runs
 * — so the design makes the conflict visible rather than pretending it cannot happen.</p>
 *
 * <p>An export token is stored only as a digest, because the feed URL discloses a host's entire
 * occupancy pattern to anyone who obtains it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("ical_connections")
public class IcalConnection {

    /** Primary key of the connection. */
    @Id
    private @Nullable UUID id;
    /** Resource whose calendar is exchanged. */
    private UUID inventoryResourceId;
    /** Which way data flows. */
    private CalendarSyncDirection direction;
    /** Feed to read from; required for an import. */
    private @Nullable String feedUrl;
    /** SHA-256 digest of the export token; required for an export, never the token itself. */
    private @Nullable String exportTokenDigest;
    /** Host-facing label naming the other platform. */
    private @Nullable String remoteLabel;
    /** Health of the connection. */
    private CalendarConnectionStatus status;
    /** How often the feed should be polled. */
    private int syncIntervalSeconds;
    /** UTC instant of the last successful sync. */
    private @Nullable Instant lastSuccessAt;
    /** UTC instant of the last failure. */
    private @Nullable Instant lastFailureAt;
    /** How many failures have occurred in a row, used to decide when to alert the host. */
    private short consecutiveFailures;
    /** UTC instant the next sync is due. */
    private @Nullable Instant nextSyncAt;
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
