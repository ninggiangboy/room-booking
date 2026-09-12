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
 * A host's challenge to an eligibility decision, and what the review concluded.
 *
 * <p>Verification decides whether someone may earn a living on the platform, and it decides partly
 * from automated screening that is known to produce false positives. An appeal route is therefore
 * structural rather than a courtesy.</p>
 *
 * <p>Two database rules keep it honest. A closed appeal must name its reviewer and its outcome,
 * because an appeal that closes with neither is a refusal with extra steps. And an overturned appeal
 * must produce a new decision through {@link #resultingDecisionId} — without one, nothing actually
 * changed and the capability the host appealed about stays exactly as it was.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("verification_appeals")
public class VerificationAppeal {

    /** Primary key of the appeal. */
    @Id
    private @Nullable UUID id;
    /** Legal profile the appeal concerns. */
    private UUID hostLegalProfileId;
    /** Decision being challenged. */
    private UUID contestedDecisionId;
    /** User who submitted the appeal. */
    private UUID submittedBy;
    /** UTC instant of submission. */
    private Instant submittedAt;
    /** The host's stated grounds, in their own words. */
    private String grounds;
    /** Reference to supporting evidence in protected storage. */
    private @Nullable String evidenceReference;
    /** Where the appeal currently sits. */
    private AppealStatus status;
    /** Operator who reviewed it. */
    private @Nullable UUID reviewerId;
    /** UTC instant of that review. */
    private @Nullable Instant reviewedAt;
    /** What the review concluded. */
    private @Nullable AppealOutcome outcome;
    /** Stable reason for that conclusion. */
    private @Nullable String outcomeReasonCode;
    /** New decision the appeal produced; required when the appeal was overturned. */
    private @Nullable UUID resultingDecisionId;
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
