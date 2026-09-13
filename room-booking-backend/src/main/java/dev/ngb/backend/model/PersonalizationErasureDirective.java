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
 * An instruction that behaviour before a named instant may no longer feed any derived profile.
 *
 * <p>The evidence cutoff is the operative field. Clearing a profile without one means tonight's batch
 * job rebuilds exactly what was cleared, so a later profile whose evidence reaches back past the
 * cutoff is refused outright.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("personalization_erasure_directives")
public class PersonalizationErasureDirective {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The account holder this row belongs to. */
    private UUID accountHolderId;
    /** Stable external reference for the directive, unique across the platform. */
    private String directiveReference;
    /** How much derived personalization the directive reaches. */
    private ErasureScope scope;
    /** Behaviour before this instant may no longer feed any derived profile. */
    private Instant evidenceCutoffAt;
    /** Why the erasure was directed. */
    private ErasureReason reason;
    /** Reference to the order or legal basis, required for a regulatory erasure. */
    private @Nullable String legalBasisReference;
    /** UTC instant the erasure was asked for. */
    private Instant requestedAt;
    /** Who asked for it. */
    private ErasureRequesterType requestedByActorType;
    /** The requested by account holder this row belongs to. */
    private @Nullable UUID requestedByAccountHolderId;
    /** Where the directive stands. */
    private ErasureDirectiveState state;
    /** UTC instant it completed, fully or partially. */
    private @Nullable Instant completedAt;
    /** UTC instant by which it must be honoured. */
    private Instant deadlineAt;
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
