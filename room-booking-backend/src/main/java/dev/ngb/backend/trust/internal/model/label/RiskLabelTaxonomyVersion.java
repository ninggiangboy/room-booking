package dev.ngb.backend.trust.internal.model.label;

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
 * One version of the vocabulary labels are written in.
 *
 * <p>Frozen once active, so a model trained last year can say which vocabulary its ground truth was
 * written in.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("risk_label_taxonomy_versions")
public class RiskLabelTaxonomyVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key; unique together with the version. */
    private String taxonomyKey;
    /** Version of this vocabulary. */
    private int taxonomyVersion;
    /** The values it permits. */
    private String[] labelValues;
    /** Where each value is defined. */
    private String definitionReference;
    /** Team accountable for it. */
    private String ownerTeam;
    /** Whether it is in use. */
    private LabelTaxonomyStatus status;
    /** When it came into use. */
    private @Nullable Instant effectiveFrom;
    /** When it stopped. */
    private @Nullable Instant effectiveUntil;
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
