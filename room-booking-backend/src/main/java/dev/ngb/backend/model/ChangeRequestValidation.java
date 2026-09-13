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
 * One check run against a proposed change.
 * <p>Append-only. Schema, policy, simulation, preview, dry run and conflict are separate because
 * they
 * fail for separate reasons, and a change reaches validated only when the ones its schema demands
 * have
 * actually run and passed.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("change_request_validations")
public class ChangeRequestValidation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The change this check was run against. */
    private UUID changeRequestId;
    /** Which check this is; each one fails for its own reason. */
    private ChangeValidationKind validationKind;
    /** Which attempt of this check the row records. */
    private int attemptNumber;
    /** What the check concluded. */
    private ChangeValidationOutcome outcome;
    /** UTC instant the check ran. */
    private Instant performedAt;
    /** The system or person that ran it. */
    private String performedBy;
    /** Where the simulation, preview or dry-run output is held. */
    private @Nullable String evidenceReference;
    /** What the check found; required for anything other than a pass. */
    private @Nullable String finding;
    /** Digest of the value that was checked, so a later edit invalidates the check. */
    private String validatedDigest;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
