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
 * One stage of applying an approved change.
 * <p>A change that reached ten per cent of one market and was stopped is not the same event as one
 * that reached everybody, so each stage is its own row. A staged rollout names the window it was
 * watched for and the check it was judged by; a canary nobody watched is a full rollout with extra
 * steps.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("configuration_rollouts")
public class ConfigurationRollout {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The approved change being rolled out. */
    private UUID changeRequestId;
    /** How far this stage of the rollout reaches. */
    private RolloutStage stage;
    /** Position of this stage within its change, unique there. */
    private int stageSequence;
    /** Fraction of traffic this stage reaches, for a staged rollout. */
    private @Nullable BigDecimal targetShare;
    /** The markets this stage reaches. */
    private @Nullable String[] targetMarkets;
    /** UTC instant the stage began. */
    private Instant startedAt;
    /** The account holder who started it. */
    private UUID startedBy;
    /** UTC instant it finished. */
    private @Nullable Instant completedAt;
    /** Where the stage stands. */
    private RolloutState rolloutState;
    /** The configuration value this stage made effective. */
    private @Nullable UUID resultingVersionId;
    /** The check the stage was judged by; a canary nobody watched is a full rollout. */
    private @Nullable String healthCheckReference;
    /** How long the stage was watched before it was allowed to proceed. */
    private @Nullable Integer observationWindowMinutes;
    /** UTC instant the stage was stopped. */
    private @Nullable Instant abortedAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String abortReason;
    /** The earlier stage this one reverses. */
    private @Nullable UUID rollbackOfRolloutId;
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
