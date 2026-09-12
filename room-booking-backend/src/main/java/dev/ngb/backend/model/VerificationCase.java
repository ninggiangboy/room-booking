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
 * One verification question being asked about a host, and the evidence gathered to answer it.
 *
 * <p>A case records what was observed. It does not confer anything: a provider returning "identity
 * matched" is evidence, and platform policy decides what capability follows from it. That decision
 * lives in {@code host_eligibility_decisions}.</p>
 *
 * <p>Cases are separated by question rather than bundled, so re-verifying an address does not reopen
 * an identity check that already passed. Only one live case of each kind may exist per profile:
 * two concurrent identity checks would race to answer the same question, and the loser's evidence
 * would silently disappear.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("verification_cases")
public class VerificationCase {

    /** Primary key of the case. */
    @Id
    private @Nullable UUID id;
    /** Legal profile being verified. */
    private UUID hostLegalProfileId;
    /** Which question the case answers. */
    private VerificationCaseType caseType;
    /** Where the case currently sits. */
    private VerificationCaseStatus status;
    /** Provider account used, so evidence stays attributable after a credential rotation. */
    private @Nullable String providerAccountKey;
    /** Version of that provider account. */
    private @Nullable Short providerAccountVersion;
    /** UTC instant the case was opened. */
    private Instant openedAt;
    /** UTC instant evidence was submitted to the provider. */
    private @Nullable Instant submittedAt;
    /** UTC instant the case concluded; paired with {@link #outcome}. */
    private @Nullable Instant completedAt;
    /** What the case concluded. */
    private @Nullable VerificationOutcome outcome;
    /** Stable reason for that conclusion. */
    private @Nullable String outcomeReasonCode;
    /** Operator who reviewed the case when automation could not conclude. */
    private @Nullable UUID manualReviewerId;
    /** UTC instant of that review; paired with {@link #manualReviewerId}. */
    private @Nullable Instant manualReviewedAt;
    /** UTC instant after which the conclusion is stale and must be re-established. */
    private @Nullable Instant expiresAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Reports whether this case still supports a conclusion at the supplied instant.
     *
     * <p>A passed case that has expired is not evidence any more. Equality on the expiry means
     * expired, matching the platform's deadline convention.</p>
     *
     * @param instant the command's decision instant
     * @return {@code true} when the case passed and has not gone stale
     */
    public boolean isCurrentlyPassedAt(Instant instant) {
        return outcome == VerificationOutcome.PASSED
                && (expiresAt == null || expiresAt.isAfter(instant));
    }
}
