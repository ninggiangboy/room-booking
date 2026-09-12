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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One answer to one approved question about one subject, in one context, with an expiry.
 *
 * <p>Not a score table: there is no row here that is not tied to a purpose. A subject with too little
 * behind it gets {@code INSUFFICIENT_EVIDENCE} and a reason rather than a low number, and a check
 * constraint refuses any answer that tries to be both.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("contextual_reputation_views")
public class ContextualReputationView {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Who the answer is about. */
    private ReputationSubjectRole subjectType;
    /** Which one. */
    private UUID subjectId;
    /** Which approved question it answers. */
    private String purposeCode;
    /** The context it was computed for. */
    private String contextKey;
    /** Purpose version that authorized it. */
    private UUID reputationPolicyVersionId;
    /** Digest of every input version used. */
    private String inputVersionsDigest;
    /** What it concluded. */
    private ReputationOutputCategory outputCategory;
    /** Underlying value, where there is one. */
    private @Nullable BigDecimal outputValue;
    /** How sure it is. */
    private @Nullable BigDecimal confidence;
    /** Why no answer could be given. */
    private @Nullable String insufficientEvidenceReason;
    /** Start of the evidence window. */
    private @Nullable Instant evidenceWindowFrom;
    /** End of that window. */
    private @Nullable Instant evidenceWindowUntil;
    /** How much evidence there was. */
    private long evidenceCount;
    /** Digest of exactly what it read. */
    private @Nullable String evidenceManifestDigest;
    /** When it was computed. */
    private Instant computedAt;
    /** When it stops being usable. */
    private Instant expiresAt;
    /** When it should be looked at again. */
    private @Nullable Instant reviewAt;
    /** View that replaced it. */
    private @Nullable UUID supersededByViewId;
    /** When it was replaced. */
    private @Nullable Instant supersededAt;
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
     * Whether a consumer may act on this answer at the given instant.
     *
     * @param at instant to test
     * @return true while live, unexpired, and actually an answer
     */
    public boolean isUsableAt(Instant at) {
        return supersededAt == null && at.isBefore(expiresAt)
                && outputCategory != ReputationOutputCategory.INSUFFICIENT_EVIDENCE;
    }
}
