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
 * Immutable lineage from an original artifact to anything derived from it.
 *
 * <p>Names its input, the tool and version that produced it, the operator, the instant and the output
 * digest, so a derivative somebody later disputes can be traced back.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("evidence_transformations")
public class EvidenceTransformation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The source evidence this row belongs to. */
    private UUID sourceEvidenceId;
    /** The derived evidence this row belongs to. */
    private UUID derivedEvidenceId;
    /** Which transformation kind this row carries. */
    private TransformationKind transformationKind;
    /** Reference to the tool, held in its owning system rather than copied here. */
    private String toolReference;
    /** Which version of the tool applies. */
    private String toolVersion;
    /** Digest of the parameters the tool ran with. */
    private @Nullable String parametersDigest;
    /** Which operator actor type this row carries. */
    private TransformationOperatorType operatorActorType;
    /** The operator account holder this row belongs to. */
    private @Nullable UUID operatorAccountHolderId;
    /** Purpose. */
    private String purpose;
    /** UTC instant performed. */
    private Instant performedAt;
    /** Digest of the output, so it can be shown later to be unchanged. */
    private String outputHash;
    /** Whether the transformation discarded information. */
    private boolean lossy;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
