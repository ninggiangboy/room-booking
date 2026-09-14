package dev.ngb.backend.trust.internal.model.decision;

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
import dev.ngb.backend.platform.JsonDocument;

/**
 * What one detector said about one revision.
 *
 * <p>Assessments are evidence, not outcomes. Several detectors may disagree about the same text and
 * all of them are kept, because a decision citing only the detector that agreed with it cannot be
 * audited. A detector that did not run has no category to offer, and a malware scan must keep the
 * evidence of what it found. Append-only.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("moderation_assessments")
public class ModerationAssessment {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The revision assessed. */
    private UUID contentRevisionId;
    /** Which detector. */
    private String detectorKey;
    /** What sort of detector it is. */
    private DetectorKind detectorKind;
    /** Which version of it; unique together with the key and revision. */
    private String detectorVersion;
    /** The policy the thresholds came from. */
    private @Nullable String policyReference;
    /** Per-category scores as the detector reported them. */
    private JsonDocument categoryScores;
    /** Highest-scoring category; absent when a fallback served. */
    private @Nullable String topCategory;
    /** Where the matched spans are held. */
    private @Nullable String protectedSpanReference;
    /** Scan evidence; required for a malware detector. */
    private @Nullable String scanEvidenceReference;
    /** Language the detector believed it was reading. */
    private @Nullable String languageDetected;
    /** Where it suggests the item goes next; never what should happen to it. */
    private ModerationRouting routingRecommendation;
    /** Why it produced no finding, or that it ran normally. */
    private DetectorServingFallback servingFallback;
    /** When it ran. */
    private Instant assessedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
