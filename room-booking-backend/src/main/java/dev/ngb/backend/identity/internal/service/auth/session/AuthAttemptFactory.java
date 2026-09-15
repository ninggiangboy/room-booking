package dev.ngb.backend.identity.internal.service.auth.session;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import dev.ngb.backend.identity.internal.model.session.AuthAttempt;
import dev.ngb.backend.identity.internal.model.session.AuthAttemptOutcome;
import dev.ngb.backend.identity.internal.model.session.AuthAttemptType;


/**
 * Constructs evidence that an authentication action was attempted.
 *
 * <p>{@code @Component} makes the factory injectable. Package-private visibility keeps
 * construction of this write-once evidence row inside the authentication package. The caller
 * supplies its own decision instant, consistent with every other factory in this package.</p>
 */
@Component
class AuthAttemptFactory {

    /**
     * Builds an unsaved attempt row with a retention deadline derived from the caller's instant.
     *
     * @param accountHolderId account the attempt resolved to, or {@code null} when it did not
     * @param identifierDigest SHA-256 digest of the identifier tried, or {@code null} when the
     *     attempt already resolved to an account
     * @param attemptType which authentication action was attempted
     * @param outcomeClass how the attempt ended
     * @param failureReasonClass stable classification of why it failed, or {@code null} on success
     * @param sourceHash SHA-256 digest of the network origin, or {@code null} when unknown
     * @param deviceHash SHA-256 digest of the device descriptor, or {@code null} when unknown
     * @param occurredAt workflow's single decision instant
     * @param retention positive duration added to {@code occurredAt} to obtain the retention deadline
     * @return attempt row to persist
     */
    AuthAttempt create(
            @Nullable UUID accountHolderId,
            @Nullable String identifierDigest,
            AuthAttemptType attemptType,
            AuthAttemptOutcome outcomeClass,
            @Nullable String failureReasonClass,
            @Nullable String sourceHash,
            @Nullable String deviceHash,
            Instant occurredAt,
            Duration retention) {
        return AuthAttempt.builder()
                // No client-generated id, unlike every other factory in this codebase: AuthAttempt
                // has no @Version (see its class Javadoc), so Spring Data JDBC's isNew() check
                // falls back to "is the @Id null". A pre-assigned id would make it issue a silent,
                // zero-row UPDATE instead of an INSERT. The database's own DEFAULT
                // gen_random_uuid() generates the id instead.
                .accountHolderId(accountHolderId)
                .identifierDigest(identifierDigest)
                .attemptType(attemptType)
                .outcomeClass(outcomeClass)
                .failureReasonClass(failureReasonClass)
                .sourceHash(sourceHash)
                .deviceHash(deviceHash)
                .occurredAt(occurredAt)
                .retainUntil(occurredAt.plus(retention))
                .build();
    }
}
