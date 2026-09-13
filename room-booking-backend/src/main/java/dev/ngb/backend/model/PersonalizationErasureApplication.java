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
 * One derived store's record of honouring one erasure directive.
 *
 * <p>Propagation to profiles, caches, training datasets, and future model builds is the part that is
 * normally forgotten, so each store is a row with an instant and a count. Anything short of applied
 * owes a written explanation.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("personalization_erasure_applications")
public class PersonalizationErasureApplication {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The directive this row belongs to. */
    private UUID directiveId;
    /** The derived store this row reports on. */
    private ErasureTargetStore targetStore;
    /** What that store managed to do. */
    private ErasureApplicationOutcome outcome;
    /** UTC instant the store acted. */
    private Instant appliedAt;
    /** How many rows the store removed or rewrote. */
    private long affectedRowCount;
    /** UTC instant up to which the cutoff has been applied, where a store honours it progressively. */
    private @Nullable Instant watermarkAppliedAt;
    /** Why the store deferred or could not comply; required whenever it did not simply apply. */
    private @Nullable String deferredReason;
    /** Reference to the operator run that performed the erasure. */
    private @Nullable String operatorReference;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
