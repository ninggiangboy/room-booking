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
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One attempt to synchronise an external calendar.
 *
 * <p>Kept as history because a feed that has quietly stopped updating looks identical to a feed with
 * nothing to report, and the difference matters: the first will eventually oversell the host.
 * Recording {@link #feedDigest} and distinguishing an unchanged run from a successful one is what
 * makes that detectable.</p>
 *
 * <p>There is no {@code @Version}: a run records what happened and is never revised.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("ical_sync_runs")
public class IcalSyncRun {

    /** Primary key of the run. */
    @Id
    private @Nullable UUID id;
    /** Connection that was synchronised. */
    private UUID icalConnectionId;
    /** UTC instant the run began. */
    private Instant startedAt;
    /** UTC instant it ended; paired with {@link #outcome}. */
    private @Nullable Instant finishedAt;
    /** How the run ended. */
    private @Nullable CalendarSyncOutcome outcome;
    /** How many calendar events the feed contained. */
    private int eventsSeen;
    /** How many blocks the run created. */
    private int blocksCreated;
    /** How many blocks the run released. */
    private int blocksReleased;
    /** How many nights the feed claimed that were already sold here. */
    private int conflictsDetected;
    /** SHA-256 digest of the feed body, so an unchanged feed is recognisable. */
    private @Nullable String feedDigest;
    /** Stable classification of the failure; never a raw provider message. */
    private @Nullable String failureClass;
    /** UTC instant the row was written. */
    private Instant createdAt;
}
