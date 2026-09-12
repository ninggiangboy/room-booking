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
 * What a review actually said, at one point in its history.
 *
 * <p>Insert-only and numbered. An edit is a new revision; the earlier one stays readable, which is
 * what lets a moderation decision be tied to the exact text it judged rather than to whatever the
 * review says today. After the cycle reveals, only correction kinds are permitted.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_revisions")
public class ReviewRevision {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Review this revision belongs to. */
    private UUID reviewRecordId;
    /** Position in the review's history. */
    private int revisionNumber;
    /** Why it was written. */
    private ReviewRevisionKind revisionKind;
    /** Overall rating from one to five. */
    private @Nullable Short overallRating;
    /** Rating schema it was written against. */
    private int ratingSchemaVersion;
    /** The words, when they are stored here rather than referenced. */
    private @Nullable String publicText;
    /** Locale the author wrote in. */
    private @Nullable String originalLocale;
    /** Language detection said it was. */
    private @Nullable String detectedLanguage;
    /** How sure that detection was. */
    private @Nullable BigDecimal detectedLanguageConfidence;
    /** Structured public answers held elsewhere. */
    private @Nullable String structuredPublicReference;
    /** Structured private answers held elsewhere. */
    private @Nullable String structuredPrivateReference;
    /** Hash of the content, which derived rows key against. */
    private String contentDigest;
    /** Client key making a retried submission idempotent. */
    private @Nullable String clientSubmissionId;
    /** Hash of that request, so a reused key with new content conflicts. */
    private @Nullable String clientRequestDigest;
    /** Who the review is from. */
    private UUID authorAccountHolderId;
    /** Who physically wrote it, when that is somebody else. */
    private @Nullable UUID actingAccountHolderId;
    /** Revision this one replaces. */
    private @Nullable UUID supersedesRevisionId;
    /** Why a correction was needed. */
    private @Nullable String correctionReason;
    /** Disclosure copy the author was shown. */
    private @Nullable String disclosureVersion;
    /** Policy version in force when it was written. */
    private UUID reviewPolicyVersionId;
    /** When the platform received it. */
    private Instant receivedAt;
    /** When it was committed. */
    private Instant committedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether this revision is a correction rather than the author changing their mind.
     *
     * <p>The distinction is what the post-reveal guard turns on: corrections stay possible after
     * a review is visible, substantive rewrites do not.</p>
     *
     * @return true for typographical, privacy and legal corrections
     */
    public boolean isCorrection() {
        return revisionKind == ReviewRevisionKind.TYPOGRAPHICAL_CORRECTION
                || revisionKind == ReviewRevisionKind.PRIVACY_REDACTION
                || revisionKind == ReviewRevisionKind.LEGAL_CORRECTION;
    }
}
