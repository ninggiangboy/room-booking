package dev.ngb.backend.ml.internal.model.feature;

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

import dev.ngb.backend.ml.internal.model.FeatureEntityKind;


/**
 * Immutable list of the exact feature versions one model reads.
 *
 * <p>The membership is the set, and it lives in {@code feature_set_members}. Freezing a set version
 * is what makes "this model may not read features it did not declare" a fact the database can check
 * rather than a rule somebody follows: a registered model names a set version, and a prediction
 * resolved against any other set version is refused.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("feature_set_versions")
public class FeatureSetVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the feature set. */
    private String setKey;
    /** Semantic version of the membership. */
    private short semanticVersion;
    /** Human-readable name for registry listings. */
    private String displayName;
    /** What models built on this set are for. */
    private String purpose;
    /** What the set is keyed by; every member must agree. */
    private FeatureEntityKind entityKind;
    /** Digest over the member definition versions, so an identical set is the same set. */
    private String memberDigest;
    /** Team accountable for the set. */
    private String businessOwner;
    /** Where the set version stands; membership can change only while it is draft. */
    private FeatureSetStatus status;
    /** UTC instant the membership was fixed. */
    private @Nullable Instant frozenAt;
    /** UTC instant the set version was withdrawn. */
    private @Nullable Instant retiredAt;
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
