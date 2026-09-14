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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import dev.ngb.backend.support.internal.model.CaseRetentionClass;
import dev.ngb.backend.support.internal.model.CaseSensitivityClass;


/**
 * What was submitted, by whom, when, and how much weight the question at hand permits it.
 *
 * <p>Provenance is a ladder rather than one confidence number. A digest proves the platform holds these
 * exact bytes; it proves nothing about whether the depicted event happened.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_evidence_items")
public class CaseEvidenceItem {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** The damage claim this row belongs to. */
    private @Nullable UUID damageClaimId;
    /** Which evidence kind this row carries. */
    private CaseEvidenceKind evidenceKind;
    /** Which source type this row carries. */
    private CaseEvidenceSourceType sourceType;
    /** Source domain. */
    private @Nullable String sourceDomain;
    /** The source object this row belongs to. */
    private @Nullable UUID sourceObjectId;
    /** Reference to the source object, held in its owning system rather than copied here. */
    private @Nullable String sourceObjectReference;
    /** The version of the source object a projected fact was read at. */
    private @Nullable Long sourceObjectVersion;
    /** Which submitted by actor type this row carries. */
    private EvidenceSubmitterType submittedByActorType;
    /** The submitted by account holder this row belongs to. */
    private @Nullable UUID submittedByAccountHolderId;
    /** The represented party account holder this row belongs to. */
    private @Nullable UUID representedPartyAccountHolderId;
    /** Which provenance class this row carries. */
    private EvidenceProvenanceClass provenanceClass;
    /** Numeric rank of the provenance class, pinned to it so ordering cannot drift. */
    private short provenanceRank;
    /** UTC instant captured. */
    private @Nullable Instant capturedAt;
    /** Whether capture metadata is actually present, which contemporaneity requires. */
    private boolean captureMetadataPresent;
    /** UTC instant received. */
    private Instant receivedAt;
    /** Where the protected bytes live; this domain never holds them. */
    private @Nullable String storageObjectReference;
    /** Which version of that stored object. */
    private @Nullable String storageObjectVersion;
    /** Digest of the bytes; it proves possession, not that the depicted event happened. */
    private @Nullable String contentHash;
    /** Which media type this row carries. */
    private @Nullable String mediaType;
    /** Byte size. */
    private @Nullable Long byteSize;
    /** Reference to the submitted filename, held safely rather than inline. */
    private @Nullable String originalFilenameReference;
    /** Language. */
    private @Nullable String language;
    /** Where the scan stands. */
    private CaseScanState scanState;
    /** Where the state stands. */
    private CaseEvidenceState state;
    /** Which sensitivity class this row carries. */
    private CaseSensitivityClass sensitivityClass;
    /** Which visibility scope this row carries. */
    private EvidenceVisibility visibilityScope;
    /** Which retention class this row carries. */
    private CaseRetentionClass retentionClass;
    /** UTC instant ordinary retention ends, absent a hold. */
    private @Nullable Instant retentionExpiresAt;
    /** Whether legal hold. */
    private boolean legalHold;
    /** Reference to the legal hold, held in its owning system rather than copied here. */
    private @Nullable String legalHoldReference;
    /** UTC instant the hold is stated to run to. */
    private @Nullable Instant legalHoldUntil;
    /** UTC instant deleted. */
    private @Nullable Instant deletedAt;
    /** Deletion method. */
    private @Nullable EvidenceDeletionMethod deletionMethod;
    /** The item this one replaces, when a correction was submitted. */
    private @Nullable UUID supersedesEvidenceId;
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
