package dev.ngb.backend.ml.internal.model.feature;

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

import dev.ngb.backend.ml.internal.model.FeatureEntityKind;


/**
 * The current served value of one feature for one entity.
 *
 * <p>A rebuildable projection rather than evidence: everything here can be recomputed from the
 * offline values and the source products, which is why it is the one table in this migration that
 * is not append-only. A trigger refuses an older source event overwriting a newer projection unless
 * the write declares itself a historical rebuild, since events do not arrive in order and a late
 * message would otherwise make the served value oscillate for no visible reason.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("online_feature_values")
public class OnlineFeatureValue {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The feature version this value is an instance of. */
    private UUID featureDefinitionId;
    /** What the value is keyed by; must match the definition. */
    private FeatureEntityKind entityKind;
    /** Hex pseudonym of the entity, never a raw identifier. */
    private String entityPseudonym;
    /** Digest of the request context, absent for entity-only features. */
    private @Nullable String contextDigest;
    /** Whether this is a measurement, an explicit absence or a substitution. */
    private FeatureValueState valueState;
    /** The value, for integer and decimal features. */
    private @Nullable BigDecimal valueNumeric;
    /** The value, for boolean, categorical and timestamp features. */
    private @Nullable String valueText;
    /** Where the value is stored, for embeddings held in analytical storage. */
    private @Nullable String valueReference;
    /** UTC instant the served value is current as of. */
    private Instant asOf;
    /** Event time of the input that produced it, used to refuse older overwrites. */
    private Instant sourceEventTime;
    /** Tie-break within one event time, for inputs that share an instant. */
    private long sourceSequence;
    /** UTC instant after which the serving path treats the value as missing. */
    private Instant expiresAt;
    /** The run that produced this value, when a batch job did. */
    private @Nullable UUID pipelineRunId;
    /** Whether this write is a declared rebuild, which may write an older state. */
    private boolean historicalRebuild;
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
