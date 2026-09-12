package dev.ngb.backend.model;

import java.math.BigDecimal;
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
 * One thing a review said about one aspect.
 *
 * <p>Frozen at insert except for inclusion, human validation and supersession: what the extractor read
 * cannot be rewritten, and a mention is excluded rather than deleted. A single mention is never a
 * finding on its own, which is why every profile value carries its counts.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_aspect_mentions")
public class ReviewAspectMention {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Run that read it. */
    private UUID reviewExtractionRunId;
    /** Review it came from. */
    private UUID reviewRecordId;
    /** Exact revision it came from. */
    private UUID reviewRevisionId;
    /** Vocabulary it was read against. */
    private UUID aspectTaxonomyVersionId;
    /** Which aspect. */
    private String aspectCode;
    /** What the mention is about. */
    private AspectTarget aspectTarget;
    /** Where in the review it came from. */
    private AspectMentionSource sourceType;
    /** Start of the span in the text. */
    private @Nullable Integer sourceSpanStart;
    /** End of that span. */
    private @Nullable Integer sourceSpanEnd;
    /** Structured answer it came from, where it did. */
    private @Nullable String structuredCategoryCode;
    /** What was said about the aspect. */
    private AspectSentiment sentiment;
    /** How strongly. */
    private @Nullable BigDecimal intensity;
    /** Whether the statement was negated. */
    private boolean negated;
    /** Qualifier attached to it. */
    private @Nullable String qualifier;
    /** How sure the extractor was. */
    private BigDecimal confidence;
    /** Language it was read in. */
    private @Nullable String sourceLanguage;
    /** Translation it was read through, where one was used. */
    private @Nullable UUID translationId;
    /** Whether a person checked it. */
    private boolean humanValidated;
    /** Which person. */
    private @Nullable UUID validatedByAccountHolderId;
    /** Mention that replaced it. */
    private @Nullable UUID supersededByMentionId;
    /** Whether it counts towards the profiles. */
    private MentionInclusionState inclusionState;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether this mention may be counted.
     *
     * @return true while qualified
     */
    public boolean counts() {
        return inclusionState == MentionInclusionState.QUALIFIED;
    }
}
