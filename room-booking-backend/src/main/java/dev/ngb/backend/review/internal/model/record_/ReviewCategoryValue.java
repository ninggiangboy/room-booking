package dev.ngb.backend.review.internal.model.record_;

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
 * One per-category rating belonging to one revision.
 *
 * <p>Part of what was said, so it is written with its revision and never appended to a submitted one:
 * adding a cleanliness score after the fact changes the review as surely as rewriting its text.</p>
 *
 * <p>A category is a rating or an explicit "not applicable"; it is never silently absent, because a
 * missing score and a score of one are different facts about the stay.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_category_values")
public class ReviewCategoryValue {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Revision this rating belongs to. */
    private UUID reviewRevisionId;
    /** Which category. */
    private String categoryCode;
    /** Category schema it was collected under. */
    private int categorySchemaVersion;
    /** Rating from one to five, when one was given. */
    private @Nullable Short ratingValue;
    /** Whether the reviewer declared the category inapplicable. */
    private boolean notApplicable;
    /** What the rating is about. */
    private ReviewCategoryTarget subjectTarget;
    /** Whether it may be shown publicly. */
    private boolean isPublic;
    /** Structured reason held elsewhere. */
    private @Nullable String structuredReasonReference;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
