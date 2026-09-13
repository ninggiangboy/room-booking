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
 * Which analytics metric a host may see, in what words, and how much evidence it needs before the
 * number is shown at all.
 * <p>Two screens computing "occupancy" two different ways is not a display inconsistency; it is two
 * different claims about the same business, and the host has no way to tell which one their
 * decision was based on. The formula, window and time zone live in migration 030's metric registry;
 * what this row adds is the host-facing wording, the evidence floor, the sentence shown below it,
 * and a statement of what the number does to the host if it moves ranking or standing. Frozen once
 * it leaves DRAFT.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_metric_publications")
public class HostMetricPublication {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /**
     * The metric from the analytics registry this publication makes visible to hosts; the
     * definition owns the formula, the window and the time zone.
     */
    private UUID metricDefinitionId;
    /**
     * Where in the host experience this metric appears; one publication per metric per surface, so
     * two screens cannot word the same number differently.
     */
    private HostMetricSurface surface;
    /** What the metric is called on the host screen, which need not be its registry key. */
    private String hostLabel;
    /** What the number means, in the words a host is shown rather than the formula. */
    private String plainExplanation;
    /** How to read the number, including what it does not mean. */
    private String interpretationGuidance;
    /** What a host can actually do about it, where there is anything honest to say. */
    private @Nullable String improvementGuidance;
    /** The sentence shown instead of a number when there is not enough evidence to work one out. */
    private String insufficientEvidenceText;
    /** How many observations the number needs before it is shown at all. */
    private int minimumObservations;
    /** Which kinds of subject this metric may be computed for. */
    private String[] subjectKinds;
    /** Whether this metric may be compared across hosts in a market benchmark. */
    private boolean benchmarkable;
    /** Whether a listings standing in search results moves with this number. */
    private boolean affectsRanking;
    /** Whether the hosts standing on the platform moves with this number. */
    private boolean affectsStanding;
    /**
     * What happens to the host as a result of this number, required whenever it affects ranking or
     * standing.
     */
    private @Nullable String consequenceExplanation;
    /** How many decimal places the number is shown to. */
    private short displayPrecision;
    /** Where this publication stands in its own lifecycle. */
    private GovernedRegistryStatus status;
    /** UTC instant the publication became visible to hosts. */
    private @Nullable Instant publishedAt;
    /** UTC instant the publication was withdrawn. */
    private @Nullable Instant retiredAt;
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
