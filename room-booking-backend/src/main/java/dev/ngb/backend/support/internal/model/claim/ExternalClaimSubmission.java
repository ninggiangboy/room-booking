package dev.ngb.backend.support.internal.model.claim;

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
 * One attempt to hand the provider a frozen manifest, with the intent written before the call.
 *
 * <p>A provider asking for more information produces a new submission version against the same claim
 * key; it never edits the manifest that was already sent.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("external_claim_submissions")
public class ExternalClaimSubmission {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The external claim this row belongs to. */
    private UUID externalClaimId;
    /** Which version of the submission applies. */
    private int submissionVersion;
    /** Which submission kind this row carries. */
    private ExternalSubmissionKind submissionKind;
    /** The key the provider deduplicates on; a retry reuses it. */
    private String idempotencyKey;
    /** The manifest this row belongs to. */
    private UUID manifestId;
    /** Digest of the frozen manifest that went with this submission. */
    private String manifestDigest;
    /** Digest of the payload actually sent. */
    private String payloadDigest;
    /** The attestation the provider requires with a submission. */
    private @Nullable String attestationReference;
    /** Which version of the terms applies. */
    private @Nullable String termsVersion;
    /** The submitted by account holder this row belongs to. */
    private @Nullable UUID submittedByAccountHolderId;
    /** Reference to the approval, held in its owning system rather than copied here. */
    private @Nullable String approvalReference;
    /** UTC instant provider deadline. */
    private @Nullable Instant providerDeadlineAt;
    /** UTC instant the intent to send was persisted, always before dispatch. */
    private Instant intentRecordedAt;
    /** UTC instant dispatched. */
    private @Nullable Instant dispatchedAt;
    /** Outcome. */
    private ExternalSubmissionOutcome outcome;
    /** Digest of the provider’s response. */
    private @Nullable String responseDigest;
    /** Reference to the response, held in its owning system rather than copied here. */
    private @Nullable String responseReference;
    /** Failure classification. */
    private @Nullable SubmissionFailureClass failureClassification;
    /** UTC instant next query. */
    private @Nullable Instant nextQueryAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
