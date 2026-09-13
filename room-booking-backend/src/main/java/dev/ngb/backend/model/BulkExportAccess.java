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
 * One retrieval of an export artifact.
 * <p>Append-only, refused outside the approved window, and counted by the database rather than by
 * the
 * caller, because the number of times a copy of the marketplace was pulled down is the figure
 * whoever
 * pulled it has the least interest in being accurate.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("bulk_export_accesses")
public class BulkExportAccess {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The export that was retrieved. */
    private UUID bulkExportRequestId;
    /** Position of this retrieval within its export, unique there. */
    private int sequenceNumber;
    /** UTC instant the retrieval happened; it must fall inside the approved window. */
    private Instant accessedAt;
    /** The account holder who retrieved it. */
    private UUID accessorId;
    /** What the retrieval did. */
    private ExportAccessKind accessKind;
    /** How many bytes were read. */
    private long byteCount;
    /**
     * Digest of the source address: enough to recognise a repeat, not another copy of a location.
     */
    private @Nullable String sourceAddressDigest;
    /** Opaque identifier of the request that carried it. */
    private @Nullable String requestId;
    /** The shared audit row this retrieval also wrote. */
    private @Nullable UUID auditEventId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
