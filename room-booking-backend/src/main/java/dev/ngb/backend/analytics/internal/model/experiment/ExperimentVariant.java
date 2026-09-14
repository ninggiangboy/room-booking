package dev.ngb.backend.analytics.internal.model.experiment;

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
import dev.ngb.backend.platform.BucketRange;

import dev.ngb.backend.platform.BucketRange;


/**
 * One arm of an epoch and the buckets it holds.
 *
 * <p>Bucket ranges are half-open and non-overlapping, enforced by an exclusion constraint rather
 * than by review, so a unit cannot hash into two arms.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("experiment_variants")
public class ExperimentVariant {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The experiment epoch this row belongs to. */
    private UUID experimentEpochId;
    /** Stable key naming this arm. */
    private String variantKey;
    /** Human-readable name for reports. */
    private String displayName;
    /** Whether this is the baseline; exactly one arm per epoch is. */
    private boolean isControl;
    /** Half-open range of buckets this arm holds; ranges within an epoch cannot overlap. */
    private BucketRange bucketRange;
    /** What the treatment actually is, held by the domain that implements it. */
    private @Nullable String treatmentReference;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
