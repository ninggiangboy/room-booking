package dev.ngb.backend.model;

import java.math.BigDecimal;
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
 * A named experiment and the namespace it competes in.
 *
 * <p>The namespace is a closed list because collision detection depends on it: two exclusive
 * experiments in one namespace may not both treat the same unit.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("experiment_definitions")
public class ExperimentDefinition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the experiment. */
    private String experimentKey;
    /** Short human name. */
    private String title;
    /** What is expected to happen and why. */
    private String hypothesis;
    /** The surface it competes for; a closed list, because collision detection depends on it. */
    private ExperimentNamespace namespace;
    /** Finer grouping within the namespace. */
    private String layer;
    /** Whether a unit in this experiment may also be in another exclusive one here. */
    private boolean exclusiveInNamespace;
    /** ISO 3166-1 alpha-2 market it is limited to, if any. */
    private @Nullable String marketCode;
    /** Who owns the decision this experiment informs. */
    private String businessOwner;
    /** Who is answerable for the analysis. */
    private String analyst;
    /** What is assumed about short-term novelty effects. */
    private @Nullable String noveltyAssumption;
    /** What is assumed about effects persisting past the experiment. */
    private @Nullable String carryoverAssumption;
    /** Whether a small stable holdout is kept to measure learning effects. */
    private boolean longTermHoldout;
    /** What fraction of traffic that holdout takes. */
    private @Nullable BigDecimal holdoutShare;
    /** Where the experiment stands across all its epochs. */
    private ExperimentStatus status;
    /** UTC instant reviewed. */
    private @Nullable Instant reviewedAt;
    /** UTC instant it was closed out. */
    private @Nullable Instant archivedAt;
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
