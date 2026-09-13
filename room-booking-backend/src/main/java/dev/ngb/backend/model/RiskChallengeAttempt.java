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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One try at satisfying a challenge.
 *
 * <p>The attempt ceiling only means something if the attempts are rows rather than a counter a
 * service can forget to increment. A trigger refuses an attempt numbered beyond the ceiling, one on a
 * challenge that has ended, and one made after the challenge expired. Append-only.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_challenge_attempts")
public class RiskChallengeAttempt {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The challenge being attempted. */
    private UUID riskChallengeId;
    /** Which try this is; unique within the challenge. */
    private short attemptNumber;
    /** How it ended. */
    private ChallengeAttemptOutcome outcome;
    /** Why it failed; required for anything but a pass. */
    private @Nullable String failureReason;
    /** Provider that assessed the proof, where one did. */
    private @Nullable UUID providerAccountId;
    /** The provider's own identifier for the attempt. */
    private @Nullable String providerReference;
    /** Coarse client description; never a full device identifier. */
    private @Nullable String clientDescriptor;
    /** When it was made. */
    private Instant attemptedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
