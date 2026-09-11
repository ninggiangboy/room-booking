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
 * A person's membership of an organization, and the role they hold in it.
 *
 * <p>Membership is not authority. The role is a label; what the member may actually do lives in
 * {@code capability_grants}, scoped to the resources they may act on, so a co-host can be given two
 * listings rather than the whole portfolio.</p>
 *
 * <p>An organization must always retain at least one active owner. That rule is about the absence of
 * other rows, so it cannot be a row constraint; the service enforces it inside the transaction that
 * would break it, backed by a reconciliation query. Removed members are retained so the actions they
 * took while active stay attributable.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("organization_members")
public class OrganizationMember {

    /** Primary key of the membership. */
    @Id
    private @Nullable UUID id;
    /** Organization account holder being joined. */
    private UUID organizationId;
    /** User who is the member. */
    private UUID userId;
    /** Role label the member holds. */
    private OrganizationMemberRole memberRole;
    /** Lifecycle of the membership. */
    private OrganizationMemberStatus status;
    /** User who issued the invitation. */
    private @Nullable UUID invitedBy;
    /** UTC instant the invitation was issued. */
    private Instant invitedAt;
    /** UTC instant the invitation was accepted; {@code null} while merely invited. */
    private @Nullable Instant joinedAt;
    /** UTC instant the member was removed; paired with the {@code REMOVED} status. */
    private @Nullable Instant removedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Reports whether grants derived from this membership should evaluate.
     *
     * @return {@code true} only while the membership is active
     */
    public boolean confersAuthority() {
        return status == OrganizationMemberStatus.ACTIVE;
    }
}
