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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * An approved reason for ignoring the policy a guest agreed to.
 *
 * <p>An earthquake, an outbreak, a platform outage: each is a named programme with an owner, a sunset
 * date and a retrospective review, so that "we waived the fee" is a decision somebody signed rather
 * than a habit.</p>
 *
 * <p>The scope and the funding are not here. They live on {@link PolicyOverrideProgramVersion}, because
 * an event's footprint widens as it unfolds and a booking judged under Tuesday's scope must stay
 * explainable after Thursday's widening.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("policy_override_programs")
public class PolicyOverrideProgram {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key, unique across the platform. */
    private String programKey;
    /** Message key for the name shown to agents and guests. */
    private String displayNameKey;
    /** The kind of event the programme responds to. */
    private OverrideEventCategory eventCategory;
    /** Who owns the programme operationally. */
    private @Nullable UUID ownerAccountHolderId;
    /** Entity whose money and obligations are behind it. */
    private @Nullable UUID legalEntityId;
    /** Market it is scoped to, where it is. */
    private @Nullable String marketCode;
    /** Whether it is draft, running, suspended or retired. */
    private ConfigurationLifecycle lifecycle;
    /** When it stops accepting applications. */
    private @Nullable Instant sunsetAt;
    /** When it was retired. */
    private @Nullable Instant retiredAt;
    /** When somebody must look back at what it cost and did. */
    private @Nullable Instant retrospectiveReviewDueAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Whether the programme is accepting applications at an instant.
     *
     * @param at instant to test
     * @return {@code true} when it is active and has not sunset
     */
    public boolean isOpenAt(Instant at) {
        return lifecycle == ConfigurationLifecycle.ACTIVE
                && (sunsetAt == null || at.isBefore(sunsetAt));
    }
}
