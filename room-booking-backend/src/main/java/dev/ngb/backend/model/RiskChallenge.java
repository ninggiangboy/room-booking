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
 * Additional proof demanded before a protected action may continue.
 *
 * <p>Bound to one actor, one action and one decision, and it expires: passing something last week is
 * not proof about what is happening now. The attempt ceiling and the expiry are fixed at issue and
 * cannot be raised afterwards, the state never moves backwards, and once it has ended no further
 * attempt may be recorded against it -- otherwise a retry loop is a brute-force tool.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_challenges")
public class RiskChallenge {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The decision that demanded the proof. */
    private UUID riskDecisionId;
    /** Who must satisfy it. */
    private UUID subjectId;
    /** Session the challenge was issued into. */
    private @Nullable UUID authSessionId;
    /** The action being gated. */
    private String protectedAction;
    /** What kind of resource the action concerns. */
    private @Nullable String resourceType;
    /** Which resource. */
    private @Nullable UUID resourceId;
    /** What kind of proof is asked for. */
    private ChallengeMethod challengeMethod;
    /** Where the challenge stands. */
    private ChallengeState state;
    /** Orderable form of the state, from {@link ChallengeState#rank()}. */
    private short stateRank;
    /** How many tries are permitted; fixed at issue. */
    private short maxAttempts;
    /** How many have been used; never falls. */
    private short attemptsUsed;
    /** Whether satisfying it can be reused for a later evaluation. */
    private boolean reusePermitted;
    /** Evidence that it was satisfied; required to pass. */
    private @Nullable String satisfiedEvidenceReference;
    /** The authentication strength passing it established. */
    private @Nullable AssuranceLevel resultingAssuranceLevel;
    /** Why it was not satisfied. */
    private @Nullable String failureReason;
    /** When it was demanded. */
    private Instant issuedAt;
    /** When it stops being answerable; fixed at issue. */
    private Instant expiresAt;
    /** When it ended, however it ended. */
    private @Nullable Instant completedAt;
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
