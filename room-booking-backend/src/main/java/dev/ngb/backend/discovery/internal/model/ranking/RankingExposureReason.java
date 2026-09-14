package dev.ngb.backend.discovery.internal.model.ranking;

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
import org.springframework.data.relational.core.mapping.Table;


/**
 * The reasons actually shown for one result, each with the evidence that supported it at the time.
 *
 * <p>A reason that cannot clear its own approved threshold is simply not written. The listing still
 * appears, without an explanation.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("ranking_exposure_reasons")
public class RankingExposureReason {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The ranking exposure this row belongs to. */
    private UUID rankingExposureId;
    /** The recommendation reason code this row belongs to. */
    private UUID recommendationReasonCodeId;
    /** One-based order the reason was shown in, unique within the result. */
    private short displayRank;
    /** Confidence of the supporting feature at the moment the claim was made. */
    private @Nullable BigDecimal supportingConfidence;
    /** Evidence weight behind it at that moment. */
    private @Nullable BigDecimal supportingEvidence;
    /** Which feature supported the claim; it must be the one the code declares. */
    private @Nullable String supportingFeatureKey;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
