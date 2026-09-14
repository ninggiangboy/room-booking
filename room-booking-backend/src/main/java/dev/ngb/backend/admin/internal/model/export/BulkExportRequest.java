package dev.ngb.backend.admin.internal.model.export;

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
import dev.ngb.backend.platform.DataPrivacyClass;

import dev.ngb.backend.platform.DataPrivacyClass;


/**
 * One bulk read of production data, approved, bounded and expiring.
 * <p>Minimisation is the columns: the classes asked for, the rows expected, the redaction applied
 * and
 * the date the artifact stops existing. An export with no expiry is a copy of the marketplace
 * living
 * somewhere nobody is monitoring, and it is the reason breach notifications name years-old
 * files.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("bulk_export_requests")
public class BulkExportRequest {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** What the data is needed for, in the words the approver will read. */
    private String purpose;
    /** Which kind of purpose this is, since analytics has an alternative the others do not. */
    private ExportPurposeClass purposeClass;
    /** The basis on which this data may leave the platform. */
    private String legalBasis;
    /** The domain that owns the data being read. */
    private String owningDomain;
    /** Which classes of data the export will contain; minimisation is this column. */
    private String[] dataClasses;
    /** The most sensitive class present. */
    private DataPrivacyClass dataSensitivity;
    /** Which redaction is applied; personal data does not leave without one. */
    private @Nullable String redactionProfile;
    /** Where the query producing the export is held. */
    private String queryReference;
    /** ISO 3166-1 alpha-2 market the data belongs to. */
    private @Nullable String marketCode;
    /** How many rows the requester expects, stated before the export runs. */
    private long estimatedRowCount;
    /** How many rows it actually contained. */
    private @Nullable Long actualRowCount;
    /** Where the artifact goes. */
    private ExportDestinationKind destinationKind;
    /** Reference to that destination. */
    private String destinationReference;
    /** The account holder who asked for it. */
    private UUID requestedBy;
    /** UTC instant it was asked for. */
    private Instant requestedAt;
    /** The account holder who approved it, never the requester. */
    private @Nullable UUID approvedBy;
    /** UTC instant it was approved. */
    private @Nullable Instant approvedAt;
    /** Where the export stands. */
    private BulkExportState exportState;
    /** UTC instant the artifact was produced. */
    private @Nullable Instant generatedAt;
    /** Digest of the artifact, so a retrieved copy can be shown to be the approved one. */
    private @Nullable String artifactDigest;
    /** Size of the artifact in bytes. */
    private @Nullable Long artifactByteSize;
    /** UTC instant the artifact stops being retrievable; every export has one. */
    private Instant expiresAt;
    /** How many times it has been retrieved, maintained by the database. */
    private int accessCount;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String rejectionReason;
    /** The account holder who withdrew it. */
    private @Nullable UUID revokedBy;
    /** UTC instant it was withdrawn. */
    private @Nullable Instant revokedAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String revocationReason;
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
