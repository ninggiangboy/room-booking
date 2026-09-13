package dev.ngb.backend.model;

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
 * The record that a treatment could actually have reached a unit.
 *
 * <p>Assignment is not exposure. An exposure cites the rule version that defined ''exposed'',
 * cannot precede its own assignment, and must declare a fallback when what was delivered differed
 * from what was assigned -- otherwise a failed model call is counted as a successful delivery and
 * every estimate downstream is biased toward zero.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("experiment_exposures")
public class ExperimentExposure {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The assignment this exposure realises. */
    private UUID experimentAssignmentId;
    /** The declared key that makes counting exposures unambiguous. */
    private String exposureDedupeKey;
    /** Where the treatment could have been seen. */
    private String surface;
    /**
     * What was actually delivered, which must match the assigned arm unless a fallback is declared.
     */
    private String actualTreatment;
    /** Whether something other than the assigned treatment was served. */
    private boolean fallbackApplied;
    /** Why, so a failed model call is not counted as a successful delivery. */
    private @Nullable String fallbackReason;
    /** Which rule defined exposed here. */
    private String exposureRuleKey;
    /** Which version of that rule; it must match the epoch. */
    private short exposureRuleVersion;
    /** What evidence supports the claim that the unit could have been reached. */
    private ExposureEvidenceSource evidenceSource;
    /** The client arrival that reported it, required for client-confirmed evidence. */
    private @Nullable UUID eventArrivalId;
    /** Opaque identifier for the request it happened in. */
    private @Nullable String requestId;
    /** Opaque identifier for the ordered candidate set it appeared in. */
    private @Nullable String resultSetId;
    /** The consuming domain decision this exposure relates to. */
    private @Nullable String decisionReference;
    /** The prediction that informed it, where one did. */
    private @Nullable String predictionReference;
    /** UTC instant the exposure happened, never before the assignment. */
    private Instant occurredAt;
    /** UTC instant it was recorded. */
    private Instant receivedAt;
    /** Which retention horizon applies. */
    private ExposureRetentionClass retentionClass;
    /** UTC instant retention may remove it. */
    private Instant expiresAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
