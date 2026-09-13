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
import org.springframework.data.relational.core.mapping.Table;

/**
 * One thing a listing may be asked to do, with the effect the host should expect from doing it.
 * <p>An item that blocks publication is a requirement, and a requirement the host may dismiss is
 * not one; calling it a suggestion while it stops the listing going live tells the host it is
 * advice and lets them discover it is a gate.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("listing_quality_checklist_items")
public class ListingQualityChecklistItem {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /**
     * The checklist version this item belongs to; membership is fixed once that version is
     * published.
     */
    private UUID checklistVersionId;
    /** Stable key naming the item within its checklist. */
    private String itemKey;
    /** Position of this item within its checklist, unique there. */
    private short ordinal;
    /** What part of the listing the item is about. */
    private ChecklistItemCategory category;
    /** What the item asks for, in one line. */
    private String title;
    /** Why it matters and what to do about it. */
    private String guidance;
    /** Where the platform looks to decide whether the item is outstanding. */
    private ChecklistEvidenceSource evidenceSource;
    /** How strongly the item is put to the host. */
    private ChecklistItemSeverity severity;
    /** Whether the listing cannot be published while this item is outstanding. */
    private boolean blocksPublication;
    /** What the host should expect if they act on it, stated rather than implied. */
    private String expectedEffectStatement;
    /** How much weight the platform puts on that expected effect. */
    private RecommendationConfidence effectConfidence;
    /** Whether the host may set the item aside. */
    private boolean hostDismissible;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
