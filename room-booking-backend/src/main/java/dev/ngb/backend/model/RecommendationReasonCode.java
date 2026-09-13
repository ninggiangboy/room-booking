package dev.ngb.backend.model;

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

/**
 * The approved vocabulary an explanation may draw from, with the evidence it must have behind it.
 *
 * <p>Reasons are attached to a result through a foreign key into this table rather than as free text,
 * because a string column cannot be asked whether the claim it carries was ever approved.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("recommendation_reason_codes")
public class RecommendationReasonCode {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The approved code clients localize. */
    private String reasonCode;
    /** Which version of the reason vocabulary this entry belongs to. */
    private int vocabularyVersion;
    /** Where the code stands in its approval lifecycle. */
    private ReasonCodeStatus status;
    /** What kind of claim the reason makes. */
    private ReasonClass reasonClass;
    /** Whether the claim is about this guest in particular. */
    private boolean personalized;
    /** Whether the claim may only be shown with recorded evidence behind it. */
    private boolean requiresEvidence;
    /** Which feature must supply that evidence. */
    private @Nullable String supportingFeatureKey;
    /** Confidence the supporting feature must reach. */
    private BigDecimal minimumConfidence;
    /** Evidence weight it must reach. */
    private BigDecimal minimumEffectiveEvidence;
    /** Key clients localize the wording from; required before the code goes live. */
    private @Nullable String localizationKey;
    /** Whether the reason must appear as a visible disclosure label. */
    private boolean disclosureLabelRequired;
    /** Reference to the disclosure review that approved the wording. */
    private @Nullable String disclosureReviewReference;
    /** The approved by account holder this row belongs to. */
    private @Nullable UUID approvedByAccountHolderId;
    /** UTC instant approved. */
    private @Nullable Instant approvedAt;
    /** UTC instant the code was retired. */
    private @Nullable Instant retiredAt;
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
