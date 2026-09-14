package dev.ngb.backend.analytics.internal.model.contract;

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

import dev.ngb.backend.analytics.internal.model.DataContractStatus;
import dev.ngb.backend.analytics.internal.model.RestatementPolicy;
import dev.ngb.backend.analytics.types.DeletionBehaviour;

/**
 * One governed version of one analytical dataset.
 *
 * <p>A dataset key may have many versions on the shelf, but only one is current, and a semantic
 * change makes a new version rather than editing this one. The row carries what a consumer has to
 * know before reading: the grain, the freshness it promises, its privacy classification, what a
 * deletion request does to it, and whether it may be used for training.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("data_product_registry")
public class DataProductVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the dataset. */
    private String datasetKey;
    /** Semantic version of this dataset definition, as major.minor.patch. */
    private String semanticVersion;
    /** Which layer of the analytical stack this dataset sits in. */
    private DataLayer dataLayer;
    /** Human-readable name shown in catalogues. */
    private String displayName;
    /** What one row of this dataset represents. */
    private String grain;
    /** Columns that uniquely identify a row at the declared grain. */
    private String primaryKeyColumns;
    /** Digest of the schema, so it can be shown later to be unchanged. */
    private String schemaDigest;
    /** Reference to the schema, held in its owning system rather than copied here. */
    private String schemaReference;
    /** Where the data itself lives; this row is the contract, not the data. */
    private String storageReference;
    /** The one accountable business or domain owner. */
    private String businessOwner;
    /** The one technical steward who maintains it. */
    private String technicalSteward;
    /** How far behind the data is expected to be. */
    private int freshnessTargetMinutes;
    /** How far behind it may fall before consumers should stop trusting it. */
    private int maxToleratedLagMinutes;
    /** How long a closed period stays open for late facts. */
    private int allowedLatenessMinutes;
    /** Which privacy class this row carries. */
    private DataPrivacyClass privacyClass;
    /** Whether any row relates to an identifiable person. */
    private boolean containsPersonalData;
    /** Whether this dataset may be used to train a model. */
    private boolean trainingAllowed;
    /** How long rows are kept before retention removes them. */
    private int retentionDays;
    /** What a subject deletion request does to rows here. */
    private DeletionBehaviour deletionBehaviour;
    /** Smallest cohort that may be released, where aggregate release applies. */
    private @Nullable Integer minimumCohortSize;
    /** Whether and how a published figure from this dataset may be restated. */
    private RestatementPolicy restatementPolicy;
    /** Where this version stands in its lifecycle. */
    private DataContractStatus status;
    /** The dataset version this one replaces. */
    private @Nullable UUID supersedesId;
    /** UTC instant reviewed. */
    private @Nullable Instant reviewedAt;
    /** UTC instant published. */
    private @Nullable Instant publishedAt;
    /** UTC instant deprecated. */
    private @Nullable Instant deprecatedAt;
    /** UTC instant retired. */
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
