package dev.ngb.backend.hostops.internal.model.bulkedit;

import java.time.Instant;
import java.time.LocalDate;
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
import dev.ngb.backend.platform.JsonDocument;

import dev.ngb.backend.platform.JsonDocument;


/**
 * One request to change many nights, listings or rate plans at once.
 * <p>The scope and the change are frozen once the preview exists, because the digest the host
 * approved describes those and nothing else. APPLIED means every target applied; anything less is
 * PARTIALLY_APPLIED, and the counts are checked against the target rows at commit. A host who reads
 * "done" and later finds sixty nights unchanged learns it from a guest complaint.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("host_bulk_edit_requests")
public class HostBulkEditRequest {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The host who asked for the edit. */
    private UUID hostAccountHolderId;
    /** Caller-supplied key making a retried request the same request rather than a second one. */
    private String idempotencyKey;
    /** What the edit changes. */
    private BulkEditKind editKind;
    /** What the edit covers, in the words the host was shown. */
    private String scopeDescription;
    /** The listings the edit covers. */
    private @Nullable UUID[] listingIds;
    /** The accommodation types the edit covers. */
    private @Nullable UUID[] accommodationTypeIds;
    /** First civil day the edit covers. */
    private @Nullable LocalDate scopeFrom;
    /** Last civil day the edit covers. */
    private @Nullable LocalDate scopeUntil;
    /** The change itself, as the host asked for it. */
    private JsonDocument requestedChange;
    /** Where the edit stands. */
    private BulkEditRequestState requestState;
    /**
     * Digest of the preview the host approved; the applied run is what this describes and nothing
     * else.
     */
    private @Nullable String previewDigest;
    /** UTC instant the preview was produced. */
    private @Nullable Instant previewedAt;
    /** How many targets the preview said the edit would touch. */
    private int targetCount;
    /** How many targets actually changed. */
    private int appliedCount;
    /** How many targets needed no change. */
    private int skippedCount;
    /** How many targets the owning domain refused. */
    private int refusedCount;
    /** Why the edit failed outright. */
    private @Nullable String failureReason;
    /** UTC instant the host asked for the edit. */
    private Instant requestedAt;
    /** UTC instant the edit finished applying. */
    private @Nullable Instant appliedAt;
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
