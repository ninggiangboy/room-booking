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
 * The record that one unit was bucketed into one arm.
 *
 * <p>Immutable, and unique per epoch and unit: two concurrent requests race here and the loser
 * reads the row the winner wrote. The salt and allocator versions are stored so the bucket can be
 * recomputed and shown to be what the hash actually produced.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("experiment_assignments")
public class ExperimentAssignment {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The experiment epoch this row belongs to. */
    private UUID experimentEpochId;
    /** The arm the unit was bucketed into. */
    private UUID experimentVariantId;
    /** What sort of thing the unit is; must match what the epoch randomises. */
    private ExperimentUnitKind unitKind;
    /** Pseudonym of the unit, never a real identifier. */
    private String unitPseudonym;
    /** The bucket the hash produced, which must fall inside the arm claimed. */
    private int bucket;
    /** Salt version used, so the bucket can be recomputed. */
    private String saltVersion;
    /** Allocator version used, for the same reason. */
    private String allocatorVersion;
    /** Digest of the eligibility inputs, so what was evaluated is recoverable. */
    private String eligibilityDigest;
    /** UTC instant eligibility was evaluated, never after the assignment. */
    private Instant eligibilityEvaluatedAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private String assignmentReason;
    /** ISO 3166-1 alpha-2 market the unit was in. */
    private @Nullable String marketCode;
    /** UTC instant the unit was bucketed. */
    private Instant assignedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
