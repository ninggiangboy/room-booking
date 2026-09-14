package dev.ngb.backend.ml.internal.model.feature;

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
 * A record that stored values of one feature have stopped being usable.
 *
 * <p>Corrections, subject deletion, withdrawn consent, an invalidated source and a retired
 * definition all make served values unusable, and all of them originate outside the model owner's
 * control. Recording the invalidation as a row the serving path reads is what makes it a decision
 * rather than a cache eviction somebody remembered to run.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("feature_invalidations")
public class FeatureInvalidation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The feature whose stored values are affected. */
    private UUID featureDefinitionId;
    /** How much of the feature's data the invalidation covers. */
    private FeatureInvalidationScope scope;
    /** The entity affected, required when the scope is one entity. */
    private @Nullable String entityPseudonym;
    /** The partition affected, required when the scope is one partition. */
    private @Nullable String partitionKey;
    /** Why the values stopped being usable. */
    private FeatureInvalidationReason reason;
    /** What happened, in prose, for whoever has to act on it. */
    private String reasonDetail;
    /** The correction that caused this, required when the reason is a correction. */
    private @Nullable UUID dataCorrectionId;
    /** UTC instant from which stored values stop being usable. */
    private Instant effectiveFrom;
    /** UTC instant the invalidation lapses; never set for deletion or withdrawn consent. */
    private @Nullable Instant effectiveTo;
    /** What the serving path does while the invalidation applies. */
    private FeatureServingBehaviour servingBehaviour;
    /** Who or what requested the invalidation. */
    private String requestedBy;
    /** UTC instant the invalidation was recorded. */
    private Instant recordedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
