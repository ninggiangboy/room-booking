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
 * Someone who works on a property they do not own.
 *
 * <p>The row records the working relationship; it never decides what the person may do.
 * {@link #capabilityGrantId} points at the property-scoped {@code capability_grants} row that does,
 * so ending a collaboration and revoking its authority are one linked act rather than two that can
 * drift apart and leave a former cleaner holding a key.</p>
 *
 * <p>Ended collaborations are retained so actions taken during them stay attributable.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("property_collaborators")
public class PropertyCollaborator {

    /** Primary key of the collaboration. */
    @Id
    private @Nullable UUID id;
    /** Property being worked on. */
    private UUID propertyId;
    /** User doing the work. */
    private UUID userId;
    /** What they do on the property. */
    private CollaboratorRole collaboratorRole;
    /** Property-scoped grant that carries what they may actually do. */
    private @Nullable UUID capabilityGrantId;
    /** Whether the collaboration is currently in force. */
    private CollaboratorStatus status;
    /** User who invited them. */
    private @Nullable UUID invitedBy;
    /** UTC instant the collaboration began. */
    private Instant startedAt;
    /** UTC instant it ended; paired with the ended status. */
    private @Nullable Instant endedAt;
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
     * Reports whether grants derived from this collaboration should evaluate.
     *
     * @return {@code true} only while the collaboration is active
     */
    public boolean confersAuthority() {
        return status == CollaboratorStatus.ACTIVE;
    }
}
