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
 * A typed link between a case and something else, without merging their authorities.
 *
 * <p>Fuzzy similarity never merges cases automatically: merging can expose one party's private
 * information to another and can collapse two distinct legal deadlines into one.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("case_relationships")
public class CaseRelationship {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** The support case this row belongs to. */
    private UUID supportCaseId;
    /** Which relation type this row carries. */
    private CaseRelationType relationType;
    /** Related domain. */
    private CaseRelatedDomain relatedDomain;
    /** The related case this row belongs to. */
    private @Nullable UUID relatedCaseId;
    /** The related object this row belongs to. */
    private @Nullable UUID relatedObjectId;
    /** Reference to the related object, held in its owning system rather than copied here. */
    private @Nullable String relatedObjectReference;
    /** The version of the related object at the time the link was made. */
    private @Nullable Long relatedObjectVersion;
    /** Direction. */
    private CaseRelationDirection direction;
    /** Which visibility scope this row carries. */
    private CaseLinkVisibility visibilityScope;
    /** Who established the link; only a person or an exact-key rule may declare a merge. */
    private RelationActorType establishedByActorType;
    /** The established by account holder this row belongs to. */
    private @Nullable UUID establishedByAccountHolderId;
    /** UTC instant established. */
    private Instant establishedAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private String reasonCode;
    /** UTC instant detached. */
    private @Nullable Instant detachedAt;
    /** Approved reason code recording why; free text never stands in for one. */
    private @Nullable String detachReason;
    /** The detached by account holder this row belongs to. */
    private @Nullable UUID detachedByAccountHolderId;
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
