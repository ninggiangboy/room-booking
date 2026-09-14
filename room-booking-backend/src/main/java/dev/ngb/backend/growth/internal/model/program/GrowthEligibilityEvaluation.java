package dev.ngb.backend.growth.internal.model.program;

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
 * One recorded decision about whether somebody qualified.
 *
 * <p>Append-only, and it keeps the sentence the guest was shown. A guest asking why they were not
 * eligible is answered from the row that decided it rather than by running today’s rules against
 * a question from last quarter.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("growth_eligibility_evaluations")
public class GrowthEligibilityEvaluation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The published terms this decision was taken under. */
    private UUID growthProgramVersionId;
    /** Whether the subject is a signed-in person or an anonymous unit. */
    private GrowthSubjectKind subjectKind;
    /** The person the decision is about. */
    private @Nullable UUID accountHolderId;
    /** Pseudonymous key standing for a visitor who is not signed in. */
    private @Nullable String anonymousUnitKey;
    /** What the rules decided. */
    private GrowthEligibilityOutcome outcome;
    /** Approved reason code recording why, present on every outcome but ELIGIBLE. */
    private @Nullable String reasonCode;
    /** The sentence the guest was shown, kept so the answer never changes afterwards. */
    private String guestExplanation;
    /** Digest of the inputs the rules ran against. */
    private String inputDigest;
    /** UTC instant the decision was taken. */
    private Instant evaluatedAt;
    /** UTC instant after which the decision must be taken again. */
    private @Nullable Instant expiresAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
