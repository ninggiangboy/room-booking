package dev.ngb.backend.discovery.internal.model.personalization;

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
 * What one guest chose about personalization, and when the choice took effect.
 *
 * <p>This is the row every derived-profile job reads first. It is not a cache and it is not derived
 * from behaviour; personalized ranking without behavioral profiling is refused as a contradiction.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("personalization_settings")
public class PersonalizationSettings {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The account holder this row belongs to. */
    private UUID accountHolderId;
    /** Whether results may be ordered for this guest in particular. */
    private boolean personalizedRanking;
    /** Whether a durable preference profile may be derived at all. */
    private boolean behavioralProfiling;
    /** Whether within-session intent may steer this session. */
    private boolean sessionIntentUse;
    /** Whether recommendation explanations may be shown. */
    private boolean explanationDisplay;
    /** Whose decision this row records. */
    private PersonalizationDecisionSource decisionSource;
    /** UTC instant the choice was made. */
    private Instant decidedAt;
    /** UTC instant the choice takes effect. */
    private Instant effectiveFrom;
    /** The market this row belongs to. */
    private @Nullable UUID marketId;
    /** Reference to the consent record, held in its owning system rather than copied here. */
    private @Nullable String consentReference;
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
