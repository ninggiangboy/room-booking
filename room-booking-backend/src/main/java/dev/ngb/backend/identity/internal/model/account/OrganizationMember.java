package dev.ngb.backend.identity.internal.model.account;

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
 *
 * <p>{@link #memberHolderId} and {@link #invitedByAccountHolderId} were fixed to their current
 * names before this module had a reader or writer: migration {@code 037} renamed the underlying
 * columns from {@code user_id}/{@code invited_by} to {@code member_holder_id}/
 * {@code invited_by_account_holder_id} once the legacy {@code users} table those names referred to
 * was retired, but the Java fields still named themselves {@code userId}/{@code invitedBy} — the
 * same class of latent mismatch migration {@code 037}'s cleanup fixed for {@code AuthAttempt}, only
 * discovered here while wiring up this module's first live service instead of while wiring up
 * {@code auth_attempts}'s. Spring Data JDBC's default naming strategy would otherwise have derived
 * {@code user_id} from the field name and failed the query against the actual column at execution
 * time, not at startup.</p>
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
    /** Account holder who is the member. */
    private UUID memberHolderId;
    /** Role label the member holds. */
    private OrganizationMemberRole memberRole;
    /** Lifecycle of the membership. */
    private OrganizationMemberStatus status;
    /** Account holder who issued the invitation. */
    private @Nullable UUID invitedByAccountHolderId;
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
