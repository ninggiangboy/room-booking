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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * What a body of reviews says about one subject, aspect by aspect.
 *
 * <p>Computed under named versions and carrying the manifest of what it read. A profile is replaced,
 * never edited, and only one may be current per subject.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("aspect_profile_versions")
public class AspectProfileVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** What this profile describes. */
    private AspectProfileSubjectType subjectType;
    /** Which one. */
    private UUID subjectId;
    /** Version of this computation. */
    private int profileVersion;
    /** Vocabulary the values are expressed in. */
    private UUID aspectTaxonomyVersionId;
    /** Aggregation logic applied. */
    private String aggregationVersion;
    /** How far through the inputs it read. */
    private @Nullable Instant inputWatermark;
    /** Digest of exactly what it read. */
    private @Nullable String sourceManifestDigest;
    /** How many reviews were behind it. */
    private long reviewCount;
    /** Whether this is the profile in force. */
    private DerivedProfileStatus status;
    /** When it was computed. */
    private Instant computedAt;
    /** When it should be recomputed. */
    private @Nullable Instant expiresAt;
    /** Profile that replaced it. */
    private @Nullable UUID supersededByProfileId;
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
