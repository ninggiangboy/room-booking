package dev.ngb.backend.analytics.internal.model.experiment;

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
 * One immutable design under which units are randomised.
 *
 * <p>The epoch carries the population, the allocation, the salt, the exposure rule and the whole
 * analysis plan, and it seals at its first assignment. After that, a material change means a new
 * epoch -- editing this one would mix two different experiments into one result set that no
 * analysis could separate.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("experiment_epochs")
public class ExperimentEpoch {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The experiment definition this row belongs to. */
    private UUID experimentDefinitionId;
    /** Which epoch of this experiment, starting at one. */
    private int epochNumber;
    /** What is randomised. */
    private ExperimentUnitKind assignmentUnit;
    /** Who is eligible. */
    private String populationExpression;
    /** Digest of that expression, so a later change is visible. */
    private String populationDigest;
    /** Who is deliberately excluded. */
    private @Nullable String exclusionExpression;
    /** How many buckets the hash produces. */
    private int bucketCount;
    /** Which salt version the hash uses; changing it re-buckets everybody. */
    private String saltVersion;
    /** Which allocator implementation produced the buckets. */
    private String allocatorVersion;
    /** Which rule defines what counts as exposed on this surface. */
    private String exposureRuleKey;
    /** Which version of that rule, so two definitions of exposed are never mixed. */
    private short exposureRuleVersion;
    /** How repeated exposures of one unit are counted. */
    private RepeatedExposurePolicy repeatedExposurePolicy;
    /** What quantity the analysis estimates. */
    private ExperimentEstimand estimand;
    /** The unit the analysis is computed at, which may be coarser than assignment. */
    private ExperimentUnitKind analysisUnit;
    /** What the clusters are, required when analysis and assignment units differ. */
    private @Nullable String clusteringExpression;
    /** Expected rate in the control arm, used to size the experiment. */
    private @Nullable BigDecimal baselineRate;
    /** Smallest effect the design can detect. */
    private BigDecimal minimumDetectableEffect;
    /** How many units the design needs. */
    private long targetSampleSize;
    /** Shortest run that is valid, whatever the numbers look like earlier. */
    private int minimumRuntimeDays;
    /** Longest the epoch may run. */
    private int maximumRuntimeDays;
    /** Error budget for the whole analysis. */
    private BigDecimal alpha;
    /** How the interval around an estimate is computed. */
    private IntervalMethod intervalMethod;
    /** How repeated looks are paid for; NONE means one look only. */
    private SequentialMethod sequentialMethod;
    /** How testing several metrics at once is paid for. */
    private MultipleComparisonCorrection multipleComparisonCorrection;
    /** What adjustment is applied, fixed before launch. */
    private VarianceReduction varianceReduction;
    /** What happens to units with no outcome. */
    private MissingDataPolicy missingDataPolicy;
    /** What result leads to what decision, written before the data exists. */
    private String decisionRule;
    /** How large an effect has to be to be worth acting on. */
    private @Nullable BigDecimal practicalSignificance;
    /** How the treatment is reverted if it has to be. */
    private String rollbackPlan;
    /** Who accepted responsibility for the design. */
    private @Nullable String approvedBy;
    /** UTC instant approved. */
    private @Nullable Instant approvedAt;
    /** Where the epoch stands; units are bucketed only while it runs. */
    private ExperimentEpochState state;
    /** UTC instant it is meant to start. */
    private @Nullable Instant scheduledStartAt;
    /** UTC instant started. */
    private @Nullable Instant startedAt;
    /** UTC instant it is meant to stop. */
    private @Nullable Instant scheduledStopAt;
    /** UTC instant stopped. */
    private @Nullable Instant stoppedAt;
    /** UTC instant the first unit was bucketed, which is when the design sealed. */
    private @Nullable Instant firstAssignmentAt;
    /**
     * Whether the design is frozen, which follows from the first assignment rather than being set
     * by hand.
     */
    private boolean sealed;
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
