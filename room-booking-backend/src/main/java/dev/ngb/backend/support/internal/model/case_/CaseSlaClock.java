package dev.ngb.backend.support.internal.model.case_;

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
 * One promise with its own deadline.
 *
 * <p>A single breached flag would hide that acknowledgement was instant and only the provider deadline
 * slipped. Pauses carry an allowlisted reason, because a pause inferred from a generic pending status
 * is how a deadline quietly stops while a customer is still waiting.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_sla_clocks")
public class CaseSlaClock {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** Lifecycle episode. */
    private short lifecycleEpisode;
    /** Which clock type this row carries. */
    private SlaClockType clockType;
    /** The support policy version this row belongs to. */
    private @Nullable UUID supportPolicyVersionId;
    /** Reference to the policy, held in its owning system rather than copied here. */
    private String policyReference;
    /** Reference to the business calendar, held in its owning system rather than copied here. */
    private String businessCalendarReference;
    /** IANA zone the civil deadlines are computed in. */
    private String timeZone;
    /** Whether the policy allows a second live clock of this type. */
    private boolean parallelDeadlinePermitted;
    /** UTC instant started. */
    private Instant startedAt;
    /** UTC instant due. */
    private Instant dueAt;
    /** Where the state stands. */
    private SlaClockState state;
    /** UTC instant paused. */
    private @Nullable Instant pausedAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable SlaPauseReason pauseReason;
    /** Total time the clock has been paused, so a due instant stays reconstructible. */
    private long pausedTotalSeconds;
    /** UTC instant completed. */
    private @Nullable Instant completedAt;
    /** What completed the clock. */
    private @Nullable String completionEventReference;
    /** UTC instant breached. */
    private @Nullable Instant breachedAt;
    /** How far the clock has escalated. */
    private short escalationLevel;
    /** Escalation route. */
    private String escalationRoute;
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
