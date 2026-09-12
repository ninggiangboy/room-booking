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
 * A photograph, video, floor plan, or tour attached to a listing.
 *
 * <p>Three independent gates gate display, because they fail independently: a file can be
 * malware-free but depict something prohibited, or be clean and permitted but not yet resized for
 * delivery. Serving an unprocessed original is both slow and a way to leak camera metadata,
 * including where a photograph was taken.</p>
 *
 * <p>{@link #contentDigest} is indexed because the same photograph uploaded against two different
 * properties is a strong duplicate-listing signal, which is one of the cheaper fraud checks
 * available.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("listing_media")
public class ListingMedia {

    /** Primary key of the media record. */
    @Id
    private @Nullable UUID id;
    /** Listing the media belongs to. */
    private UUID listingId;
    /** Kind of media. */
    private ListingMediaType mediaType;
    /** Location of the stored file. */
    private String storageReference;
    /** SHA-256 digest of the content, also used for duplicate detection. */
    private String contentDigest;
    /** IANA media type of the stored file. */
    private String contentType;
    /** Size of the stored file in bytes. */
    private long byteSize;
    /** Pixel width, for still and video media. */
    private @Nullable Integer widthPx;
    /** Pixel height, for still and video media. */
    private @Nullable Integer heightPx;
    /** Duration in milliseconds, for time-based media. */
    private @Nullable Integer durationMs;
    /** Alternative text describing the media for readers who cannot see it. */
    private @Nullable String altText;
    /** Which room or area the media depicts. */
    private @Nullable String roomTag;
    /** Position in the gallery. */
    private short displayOrder;
    /** Whether this is the listing's cover image; at most one per listing. */
    private boolean isCover;
    /** Malware-scan result. */
    private DocumentScanState scanState;
    /** Whether the depicted content has been cleared. */
    private ContentModerationState moderationState;
    /** Whether delivery renditions exist. */
    private MediaProcessingState processingState;
    /** UTC instant the host confirmed they hold the rights to publish this. */
    private @Nullable Instant rightsConfirmedAt;
    /** UTC instant the file was uploaded. */
    private Instant uploadedAt;
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
     * Reports whether this media may be shown to a guest.
     *
     * <p>All three gates must pass. Any one of them failing is a reason not to serve the file, and
     * checking only moderation is how an unscanned upload reaches a viewer.</p>
     *
     * @return {@code true} when the file is scanned clean, approved, and processed
     */
    public boolean isDisplayable() {
        return scanState == DocumentScanState.CLEAN
                && moderationState == ContentModerationState.APPROVED
                && processingState == MediaProcessingState.READY;
    }
}
