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


/**
 * A declared relationship between one epoch and another experiment.
 *
 * <p>Mutual exclusion, a required co-experiment and a known interaction are different statements.
 * Recording them is what lets an accidental overlap be refused at assignment rather than
 * reinterpreted afterwards as a factorial design.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("experiment_exclusions")
public class ExperimentExclusion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The epoch declaring the relationship. */
    private UUID experimentEpochId;
    /** The other experiment it relates to. */
    private UUID relatedExperimentDefinitionId;
    /** How the two relate. */
    private ExperimentRelationKind relationKind;
    /** Why, so the declaration can be reviewed rather than taken on trust. */
    private String reason;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
