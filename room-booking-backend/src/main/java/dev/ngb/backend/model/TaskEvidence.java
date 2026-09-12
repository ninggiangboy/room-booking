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
 * Something submitted in support of a task attestation.
 *
 * <p>The platform may check that a photo was taken in the right window near the right place. It may
 * not conclude from that alone that the room is clean, so these rows support a completion rather than
 * constituting one.</p>
 *
 * <p>Frozen at insert except for the scan result, the classification and the legal hold. A held row
 * cannot be deleted at all.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("task_evidence")
public class TaskEvidence {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Task this supports. */
    private UUID operationalTaskId;
    /** Form the evidence takes. */
    private TaskEvidenceType evidenceType;
    /** Where it came from. */
    private OperationalEvidenceSource source;
    /** Who submitted it. */
    private @Nullable UUID submittedByAccountHolderId;
    /** Reference to the stored artifact. */
    private @Nullable String objectReference;
    /** Hash of that artifact. */
    private @Nullable String contentHash;
    /** Whether malware scanning cleared it. */
    private EvidenceScanState scanState;
    /** When it was captured, where that is known. */
    private @Nullable Instant capturedAt;
    /** When the platform received it. */
    private Instant receivedAt;
    /** Device that produced it. */
    private @Nullable String deviceReference;
    /** How much weight the supporting signal can carry. */
    private EvidenceConfidence signalQuality;
    /** How restricted the material is. */
    private SensitivityClass sensitivityClass;
    /** How long it is kept. */
    private RetentionClass retentionClass;
    /** Whether deletion is barred. */
    private boolean legalHold;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Whether this row may count towards an evidence requirement.
     *
     * <p>Unscanned and infected uploads do not count; the trigger enforcing completion applies the
     * same test, so that the standard of proof is never merely "something was uploaded".</p>
     *
     * @return true once scanning cleared it or did not apply
     */
    public boolean isUsable() {
        return scanState == EvidenceScanState.CLEAN
                || scanState == EvidenceScanState.NOT_APPLICABLE;
    }
}
