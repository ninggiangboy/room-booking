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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A time-bounded, confidence-scored relationship between two subjects.
 *
 * <p>Shared addresses, devices and networks are the everyday condition of hotels, families and
 * offices, so a link is a question rather than a finding. Using one to justify an adverse decision
 * requires independent corroboration and a confidence the platform is prepared to defend, which is a
 * check constraint on this row. Links expire: a relationship observed once three years ago is not
 * evidence about today, and letting it persist turns a graph into a permanent record of who once
 * shared a hotel network with whom. Undirected pairs are stored once, in a canonical endpoint
 * order.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("entity_links")
public class EntityLink {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** One endpoint; the lower identifier when the relation is undirected. */
    private UUID endpointASubjectId;
    /** The other endpoint. */
    private UUID endpointBSubjectId;
    /** What the two appear to have in common. */
    private EntityLinkRelation relation;
    /** Whether the relationship has a direction. */
    private EntityLinkDirection direction;
    /** How it was established. */
    private EntityLinkEvidenceClass evidenceClass;
    /** How confident the platform is, between zero and one. */
    private BigDecimal confidence;
    /** Independent observations behind it; at least two for adverse use. */
    private short corroboratingEvidenceCount;
    /** How many times it has been seen. */
    private long observationCount;
    /** Where the observations came from. */
    private String sourceDomain;
    /** What the link may be used for. */
    private String[] permittedPurposes;
    /** Whether it may contribute to an adverse decision. */
    private boolean adverseUsePermitted;
    /** When the relationship was first observed. */
    private Instant firstSeenAt;
    /** When it was last observed. */
    private Instant lastSeenAt;
    /** When it stops being evidence; always after it was last seen. */
    private Instant expiresAt;
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
