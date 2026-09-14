package dev.ngb.backend.trust.internal.model.content;

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
 * Somebody's allegation about an exact revision.
 *
 * <p>A report is evidence that a complaint was made, not evidence that the complaint is true. The
 * reporter's identity is confidential by default and anything else needs a recorded authorization,
 * because a reporter whose identity leaks is a reporter who will not report next time. One reporter,
 * one revision, one category, one report: repeat submissions are the same complaint, while brigading
 * stays visible as many reporters. An urgent safety report must carry a review task, and nothing here
 * couples a report to a restriction -- the intake path stays open to somebody the platform has
 * otherwise limited.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("content_reports")
public class ContentReport {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The exact revision complained about. */
    private UUID contentRevisionId;
    /** Who reported it. */
    private UUID reporterSubjectId;
    /** How they relate to what they reported. */
    private ReporterRelationship reporterRelationship;
    /** What they allege. */
    private String category;
    /** How urgently it must be looked at. */
    private ReportSeverityBand severityBand;
    /** Where their own account of it is held. */
    private @Nullable String allegationReference;
    /** Language they reported in. */
    private @Nullable String languageTag;
    /** Group joining reports that concern one investigation. */
    private @Nullable UUID dedupeGroupId;
    /** The task handling it; required for an urgent safety report. */
    private @Nullable UUID riskReviewTaskId;
    /** Whether the reported party may learn who reported them. */
    private ReporterDisclosure reporterDisclosure;
    /** The authorization behind any disclosure. */
    private @Nullable String disclosureAuthorizationReference;
    /** When receipt was confirmed to the reporter. */
    private @Nullable Instant acknowledgedAt;
    /** What the platform concluded. */
    private @Nullable ReportAdjudication adjudicatedOutcome;
    /** When it concluded that. */
    private @Nullable Instant adjudicatedAt;
    /** The decision that answered it. */
    private @Nullable UUID moderationDecisionId;
    /** When the reported thing happened. */
    private Instant eventTime;
    /** When the report arrived. */
    private Instant reportedAt;
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
