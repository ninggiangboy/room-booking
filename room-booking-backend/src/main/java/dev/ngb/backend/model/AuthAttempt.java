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
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Evidence that an authentication action was attempted, whatever came of it.
 *
 * <p>Failures matter more than successes here: velocity control and account-takeover investigation
 * are built on the attempts that did not work. The row holds no credential material at all.</p>
 *
 * <p>An attempt that resolved to an account names it. One that did not carries only a digest of the
 * identifier that was tried — storing the raw value would turn the failure log into a directory of
 * which email addresses exist on the platform. Source and device are hashed for the same reason.</p>
 *
 * <p>There is no {@code @Version}: the row is written once and never revised.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("auth_attempts")
public class AuthAttempt {

    /** Primary key of the attempt. */
    @Id
    private @Nullable UUID id;
    /** Account the attempt resolved to, where it resolved to one. */
    private @Nullable UUID userId;
    /** SHA-256 digest of the identifier tried, where no account was resolved. */
    private @Nullable String identifierDigest;
    /** Which authentication action was attempted. */
    private AuthAttemptType attemptType;
    /** How the attempt ended. */
    private AuthAttemptOutcome outcomeClass;
    /** Stable classification of why it failed; never a raw message. */
    private @Nullable String failureReasonClass;
    /** SHA-256 digest of the network origin. */
    private @Nullable String sourceHash;
    /** SHA-256 digest of the device descriptor. */
    private @Nullable String deviceHash;
    /** Reference to the risk decision consulted, where one was. */
    private @Nullable String decisionReference;
    /** UTC instant the attempt occurred. */
    private Instant occurredAt;
    /** UTC instant after which the row is pruned; the window is bounded and approved. */
    private Instant retainUntil;
}
