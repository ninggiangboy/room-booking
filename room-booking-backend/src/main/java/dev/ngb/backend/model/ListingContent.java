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
 * A listing's wording in one language.
 *
 * <p>Exactly one locale per listing is the source — what the host actually wrote — and the rest are
 * translations of it. Without that distinction there is no answer to "which wording is
 * authoritative" when two translations disagree, and a guest disputing what a listing promised has
 * nothing to point at.</p>
 *
 * <p>Content is host-supplied text shown to strangers, so it passes moderation before display.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("listing_contents")
public class ListingContent {

    /** Primary key of the content row. */
    @Id
    private @Nullable UUID id;
    /** Listing this wording belongs to. */
    private UUID listingId;
    /** BCP 47 locale this wording is written in. */
    private String locale;
    /** Whether this is the host's own wording rather than a translation of it. */
    private boolean isSource;
    /** Who produced this language version. */
    private TranslationSource translationSource;
    /** Headline shown in search results and on the listing page. */
    private String title;
    /** Short summary. */
    private @Nullable String summary;
    /** Full description of the space. */
    private @Nullable String description;
    /** What the host says about the surrounding area. */
    private @Nullable String neighbourhoodNote;
    /** Whether the wording has been cleared for display. */
    private ContentModerationState moderationState;
    /** UTC instant moderation concluded. */
    private @Nullable Instant moderatedAt;
    /** Monotonic version, incremented on each edit so history can cite an exact wording. */
    private int contentVersion;
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
     * Reports whether this wording may be shown to a guest.
     *
     * @return {@code true} only when moderation has approved it
     */
    public boolean isDisplayable() {
        return moderationState == ContentModerationState.APPROVED;
    }
}
