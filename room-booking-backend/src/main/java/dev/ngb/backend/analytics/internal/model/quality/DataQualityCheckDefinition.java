package dev.ngb.backend.analytics.internal.model.quality;

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
 * One versioned quality check against one dataset version.
 *
 * <p>Each check names the dimension it tests, how serious a failure is, and what a consumer should
 * do about one. A check declared fatal may not also declare its failure acceptable.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("data_quality_check_definitions")
public class DataQualityCheckDefinition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming this check. */
    private String checkKey;
    /** Which version of the check this row defines. */
    private short checkVersion;
    /** The dataset version it tests. */
    private UUID dataProductId;
    /** What aspect of the data it tests. */
    private QualityDimension qualityDimension;
    /** What the check asserts. */
    private String description;
    /** Where the check expression itself lives. */
    private String expressionReference;
    /** The threshold a result is compared against. */
    private @Nullable String thresholdExpression;
    /** How serious a failure is. */
    private QualitySeverity severity;
    /** What a consumer should do when it fails. */
    private QualityConsumerBehaviour consumerBehaviour;
    /** Who is answerable for this check. */
    private String owner;
    /** Whether the check is evaluated on every run. */
    private QualityCheckStatus status;
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
