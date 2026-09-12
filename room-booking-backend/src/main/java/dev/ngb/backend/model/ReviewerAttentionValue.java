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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One aspect within one reviewer attention profile.
 *
 * <p>What is shared with discovery is this minimized view, not the review text it came from. The stay
 * and mention counts travel with the score, so a consumer cannot use the number without seeing how
 * thin it is.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("reviewer_attention_values")
public class ReviewerAttentionValue {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Profile this value belongs to. */
    private UUID reviewerAttentionProfileId;
    /** Which aspect. */
    private String aspectCode;
    /** How much this reviewer attends to it, from zero to one. */
    private BigDecimal attentionScore;
    /** What they have tended to say about it. */
    private @Nullable BigDecimal sentimentEvidence;
    /** How many separate stays produced the mentions. */
    private int distinctStayCount;
    /** How many mentions were counted. */
    private long mentionCount;
    /** How sure the computation is. */
    private BigDecimal confidence;
    /** How much recent evidence dominates. */
    private @Nullable BigDecimal recencyWeight;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
