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
 * The immutable payload of a pricing rule.
 *
 * <p>Once published, the condition and action are frozen. Priced nights cite this row, so changing it
 * afterwards would not record a change — it would rewrite history, and a guest's receipt would stop
 * matching the rule it claims to follow.</p>
 *
 * <p>The freeze is enforced by database triggers rather than by this class, because a rule that only
 * the application protects is a rule one forgotten code path can undo.
 * {@code trg_price_rule_versions_freeze} refuses edits to a published payload and refuses a return to
 * {@code DRAFT}; {@code trg_price_rule_versions_no_delete} refuses deletion. The only forward move is
 * {@link PublicationState#RETIRED}, which ends the version's future use without altering it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("price_rule_versions")
public class PriceRuleVersion {

    /** Primary key of the version. */
    @Id
    private @Nullable UUID id;
    /** Rule this is a version of. */
    private UUID priceRuleId;
    /** Monotonic number within the rule, starting at one. */
    private int versionNumber;
    /** When the rule applies, as an immutable JSON condition. */
    private JsonDocument conditionPayload;
    /** What it does to the price, as an immutable JSON action. */
    private JsonDocument actionPayload;
    /** Lowercase hex SHA-256 of the payload, so an identical resubmission is recognisable. */
    private String contentDigest;
    /** Whether this version may be cited, and whether it may still be edited. */
    private PublicationState publicationState;
    /** UTC instant the version began applying; required once published. */
    private @Nullable Instant effectiveFrom;
    /** UTC instant it stopped; absent while it is open-ended. */
    private @Nullable Instant effectiveUntil;
    /** Account holder who wrote it. */
    private @Nullable UUID authoredBy;
    /** Account holder who approved it; required once published. */
    private @Nullable UUID approvedBy;
    /** UTC instant it was published and became immutable. */
    private @Nullable Instant publishedAt;
    /** UTC instant it was retired from future pricing. */
    private @Nullable Instant retiredAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Reports whether this version was in force at an instant.
     *
     * @param instant the command's decision instant
     * @return {@code true} when the version is published and the instant falls in its period
     */
    public boolean isInForceAt(Instant instant) {
        return publicationState == PublicationState.PUBLISHED
                && effectiveFrom != null
                && !instant.isBefore(effectiveFrom)
                && (effectiveUntil == null || instant.isBefore(effectiveUntil));
    }

    /**
     * Reports whether the payload can still be changed.
     *
     * <p>Advisory only. The database refuses the write regardless, which is the guarantee that
     * matters; this exists so a caller can say so before attempting it.</p>
     *
     * @return {@code true} only while the version is a draft
     */
    public boolean isEditable() {
        return publicationState == PublicationState.DRAFT;
    }
}
