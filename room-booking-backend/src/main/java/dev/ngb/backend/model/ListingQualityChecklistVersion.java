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
 * A published set of things a listing needs before it competes on equal terms.
 * <p>Frozen once published, and its membership is frozen with it: appending a ninth item to a live
 * checklist would silently give every listing an outstanding action nobody versioned. A different
 * set of items is a new version.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("listing_quality_checklist_versions")
public class ListingQualityChecklistVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable key naming the checklist across its versions. */
    private String checklistKey;
    /** Which version of the checklist the row is. */
    private int checklistVersion;
    /** ISO 3166-1 alpha-2 market the checklist applies in, or null where it applies everywhere. */
    private @Nullable String marketCode;
    /** What the checklist is called on the host screen. */
    private String displayName;
    /** What the checklist is for, in the words a host is shown. */
    private String purpose;
    /** Where this checklist version stands in its own lifecycle. */
    private GovernedRegistryStatus status;
    /** UTC instant the checklist became the one listings are measured against. */
    private @Nullable Instant publishedAt;
    /** UTC instant the checklist was withdrawn. */
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
}
