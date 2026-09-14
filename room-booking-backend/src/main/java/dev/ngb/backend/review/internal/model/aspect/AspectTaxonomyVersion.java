package dev.ngb.backend.review.internal.model.aspect;

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


/**
 * One version of the vocabulary extraction is allowed to use.
 *
 * <p>Frozen once active, aspects included: every mention and every profile value names the version it
 * was produced under, so a taxonomy edited in place would silently change what stored rows mean.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("aspect_taxonomy_versions")
public class AspectTaxonomyVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable identity across versions. */
    private String taxonomyKey;
    /** Version within that taxonomy. */
    private int taxonomyVersion;
    /** Where this version stands. */
    private AspectTaxonomyStatus status;
    /** When it came into use. */
    private @Nullable Instant effectiveFrom;
    /** When it stopped being used. */
    private @Nullable Instant effectiveUntil;
    /** Hash of the vocabulary. */
    private String checksum;
    /** Who approved it. */
    private @Nullable UUID approvedByAccountHolderId;
    /** When it was approved. */
    private @Nullable Instant approvedAt;
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
