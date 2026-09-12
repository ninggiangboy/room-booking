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
 * What one reviewer repeatedly notices, derived from their own reviews for a named purpose.
 *
 * <p>Attention is not preference and not sentiment: that somebody always mentions noise says what they
 * attend to, not what they want. This is personal data about a person, so it carries an opt-out, and a
 * check constraint stops an opted-out profile being current -- a consumer reading only the status would
 * otherwise still be handed the data.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("reviewer_attention_profiles")
public class ReviewerAttentionProfile {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Reviewer described. */
    private UUID reviewerAccountHolderId;
    /** Version of this computation. */
    private int profileVersion;
    /** What the profile may be used for. */
    private String purposeCode;
    /** Vocabulary the values are expressed in. */
    private UUID aspectTaxonomyVersionId;
    /** Start of the window it read. */
    private @Nullable Instant sourceWindowFrom;
    /** End of that window. */
    private @Nullable Instant sourceWindowUntil;
    /** How many separate stays were behind it. */
    private int distinctStayCount;
    /** How far through the inputs it read. */
    private @Nullable Instant inputWatermark;
    /** Digest of exactly what it read. */
    private @Nullable String sourceManifestDigest;
    /** Whether it may be read. */
    private AttentionProfileStatus status;
    /** Whether the person asked to be left out. */
    private boolean optedOut;
    /** When they asked for deletion. */
    private @Nullable Instant deletionRequestedAt;
    /** When it was computed. */
    private Instant computedAt;
    /** When it should be recomputed or dropped. */
    private @Nullable Instant expiresAt;
    /** Profile that replaced it. */
    private @Nullable UUID supersededByProfileId;
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
     * Whether a consumer may read this profile at the given instant.
     *
     * @param at instant to test
     * @return true when current, not opted out, and not expired
     */
    public boolean isReadableAt(Instant at) {
        return status == AttentionProfileStatus.CURRENT && !optedOut
                && (expiresAt == null || at.isBefore(expiresAt));
    }
}
