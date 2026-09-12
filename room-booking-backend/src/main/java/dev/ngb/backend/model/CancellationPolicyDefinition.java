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
 * A cancellation policy family: a stable identity a host or a market can select.
 *
 * <p>Selecting a family is not selecting terms. Nothing here carries a rule -- the rules live on
 * {@link CancellationPolicyVersion}, which is what a booking actually cites.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("cancellation_policy_definitions")
public class CancellationPolicyDefinition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key, unique across the platform. */
    private String policyKey;
    /** Message key for the host-facing name. */
    private String displayNameKey;
    /** Who owns the family and may publish versions of it. */
    private CancellationPolicyOwner ownerType;
    /** The owning host, for a host-owned family. */
    private @Nullable UUID ownerAccountHolderId;
    /** The market this family belongs to, where it is market-scoped. */
    private @Nullable String marketCode;
    /** Contracting entity behind the terms, where one applies. */
    private @Nullable UUID legalEntityId;
    /**
     * What supply this family may be attached to. A family built for long stays must not silently
     * become selectable for a one-night booking.
     */
    private String[] appliesToProductTypes;
    /** Shortest stay the family may be attached to. */
    private @Nullable Short minimumNights;
    /** Longest stay the family may be attached to. */
    private @Nullable Short maximumNights;
    /** Whether a host may choose this family themselves. */
    private boolean hostSelectable;
    /** Whether the family is draft, offered, suspended or retired. */
    private ConfigurationLifecycle lifecycle;
    /** When it stopped being offered. */
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

    /**
     * Whether the family may be attached to new supply.
     *
     * @return {@code true} when it is active
     */
    public boolean isSelectable() {
        return lifecycle == ConfigurationLifecycle.ACTIVE;
    }
}
