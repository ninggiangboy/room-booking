package dev.ngb.backend.support.internal.model.evidence;

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
 * A presentation derivative, never a destruction.
 *
 * <p>The protected original stays where it was; this row records what was hidden, on what ground, by
 * whom, and whether an independent reviewer checked.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("evidence_redactions")
public class EvidenceRedaction {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The source evidence this row belongs to. */
    private UUID sourceEvidenceId;
    /** The derived evidence this row belongs to. */
    private UUID derivedEvidenceId;
    /** The transformation this row belongs to. */
    private UUID transformationId;
    /** Approved reason code recording why; free text never stands in for one. */
    private RedactionReason redactionReason;
    /** Legal basis. */
    private @Nullable String legalBasis;
    /** The regions removed, named so the redaction can be checked. */
    private String[] redactedRegions;
    /** The fields removed, named for the same reason. */
    private String[] redactedFields;
    /** The operator account holder this row belongs to. */
    private UUID operatorAccountHolderId;
    /** UTC instant performed. */
    private Instant performedAt;
    /** The reviewed by account holder this row belongs to. */
    private @Nullable UUID reviewedByAccountHolderId;
    /** UTC instant reviewed. */
    private @Nullable Instant reviewedAt;
    /** Review outcome. */
    private @Nullable RedactionReviewOutcome reviewOutcome;
    /** Digest of the output, so it can be shown later to be unchanged. */
    private String outputHash;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
