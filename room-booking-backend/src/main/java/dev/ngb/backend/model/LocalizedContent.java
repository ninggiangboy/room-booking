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
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One immutable, reviewed rendering of a semantic message in one locale.
 *
 * <p>Content renders decisions; it never makes them. A disclosure string is what the guest was
 * shown, and the acceptance recorded against a booking cites the exact version, so what a guest
 * agreed to can be reproduced verbatim after the wording changes.</p>
 *
 * <p>An {@code APPROVED} row must name its reviewer: the database refuses one that does not, because
 * content presenting a legal disclosure with no accountable reviewer is exactly the content that
 * must not reach a guest.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("localized_contents")
public class LocalizedContent {

    /** Primary key of the content version. */
    @Id
    private @Nullable UUID id;
    /** Stable semantic key, such as {@code cancellation.disclosure}. */
    private String contentKey;
    /** BCP 47 locale this rendering is written in. */
    private String locale;
    /** Monotonic version number within this key and locale. */
    private int contentVersion;
    /** Rendered text shown to the reader. */
    private String body;
    /** SHA-256 digest of {@link #body}, proving what was reviewed. */
    private String contentDigest;
    /** Market the content is scoped to; {@code null} when it applies everywhere. */
    private @Nullable UUID marketId;
    /** Policy bundle this wording renders, where it renders one. */
    private @Nullable UUID policyBundleId;
    /** Operator who approved the wording. */
    private @Nullable UUID reviewerId;
    /** UTC instant of that approval. */
    private @Nullable Instant reviewedAt;
    /** Whether the version is renderable. */
    private ContentLifecycle lifecycleState;
    /** UTC instant from which the version renders, inclusive. */
    private Instant effectiveFrom;
    /** UTC instant from which it stops, exclusive; {@code null} while current. */
    private @Nullable Instant effectiveUntil;
    /** UTC instant the row was written. */
    private Instant createdAt;
}
