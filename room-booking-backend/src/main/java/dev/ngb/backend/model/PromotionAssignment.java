package dev.ngb.backend.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
 * A promotion offered to one subject, decided before they ever priced a trip.
 *
 * <p>Separating assignment from redemption is what makes a campaign measurable. The guests who were
 * eligible and did not book are as much a part of the result as those who did, and without this row
 * they are invisible.</p>
 *
 * <p>An assignment is addressed either to a known account or to an anonymous unit, never both and
 * never neither — an assignment nobody can claim is not an assignment.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("promotion_assignments")
public class PromotionAssignment {

    /** Primary key of the assignment. */
    @Id
    private @Nullable UUID id;
    /** Terms the subject was offered. */
    private UUID promotionVersionId;
    /** Guest offered the promotion, when they are signed in. */
    private @Nullable UUID guestAccountHolderId;
    /** Stable anonymous unit offered it instead, when they are not. */
    private @Nullable String anonymousUnitKey;
    /** Experiment this assignment belongs to, when it is part of one. */
    private @Nullable String experimentKey;
    /** Arm of that experiment; present exactly when the experiment is. */
    private @Nullable String experimentArm;
    /** Facts that made the subject eligible, as an immutable JSON snapshot. */
    private @Nullable JsonDocument eligibilitySnapshot;
    /** UTC instant the offer was made. */
    private Instant assignedAt;
    /** UTC instant it stops being claimable. */
    private @Nullable Instant expiresAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
