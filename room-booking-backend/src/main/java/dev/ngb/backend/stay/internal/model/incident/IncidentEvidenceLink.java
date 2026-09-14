package dev.ngb.backend.stay.internal.model.incident;

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
import dev.ngb.backend.platform.RetentionClass;
import dev.ngb.backend.platform.SensitivityClass;
import dev.ngb.backend.platform.TimelineVisibility;

/**
 * One artifact linked to an incident, with a purpose and a custody record.
 *
 * <p>Nothing is copied. Private conversation content is never bulk-imported into a case; selected
 * material is linked with purpose-scoped authorization, which is what makes a later transfer to
 * claims or trust auditable.</p>
 *
 * <p>Custody, visibility and retention move over its working life. What it points at does not: a
 * trigger refuses repointing, so a decision record cannot come to cite something it never saw.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("incident_evidence_links")
public class IncidentEvidenceLink {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Case this belongs to. */
    private UUID incidentId;
    /** Domain that owns the artifact. */
    private EvidenceSourceDomain sourceDomain;
    /** Kind of artifact. */
    private EvidenceSourceType sourceType;
    /** Identity of the artifact in that domain. */
    private UUID sourceId;
    /** Version of it that was linked. */
    private @Nullable Long sourceVersion;
    /** Why it is in this case. */
    private String purposeCode;
    /** Who may see it here. */
    private TimelineVisibility visibility;
    /** Who linked it. */
    private @Nullable UUID linkedByAccountHolderId;
    /** When it was linked. */
    private Instant linkedAt;
    /** Hash of the artifact as linked. */
    private @Nullable String contentHash;
    /** Who currently holds it. */
    private EvidenceCustodyState custodyState;
    /** Domain it was handed to. */
    private @Nullable IncidentTransferDomain transferredToDomain;
    /** That domain reference for it. */
    private @Nullable String transferredReference;
    /** How restricted it is. */
    private SensitivityClass sensitivityClass;
    /** How long it is kept. */
    private RetentionClass retentionClass;
    /** Whether deletion is barred. */
    private boolean legalHold;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Whether this evidence may be shown to a participant.
     *
     * @return true unless it is internal-only
     */
    public boolean isParticipantVisible() {
        return visibility != TimelineVisibility.INTERNAL;
    }
}
