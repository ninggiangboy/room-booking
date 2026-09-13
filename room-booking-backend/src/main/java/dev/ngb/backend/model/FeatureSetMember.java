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
 * One feature version's membership of one feature set version.
 *
 * <p>Append-only, sealed once the set leaves DRAFT, and constrained so that neither the feature nor
 * the ordinal can repeat within a set -- a vector whose column order is ambiguous is one the
 * serving path and the training path can disagree about without either of them raising
 * anything.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("feature_set_members")
public class FeatureSetMember {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The feature set version this membership belongs to. */
    private UUID featureSetVersionId;
    /** The exact feature version the set reads. */
    private UUID featureDefinitionId;
    /** Position within the set, unique there, so the vector's column order is unambiguous. */
    private int ordinal;
    /** Whether a missing value for this member blocks the model or is imputed. */
    private boolean required;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
