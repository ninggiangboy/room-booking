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
 * An approved, deterministic mapping from one kind of source fact to a set of postings.
 *
 * <p>A transaction pins the version it used. Replaying a two-year-old booking therefore produces the
 * entries that were correct two years ago rather than the entries today's chart of accounts would
 * produce, and there is no generic latest-rule lookup for historical replay.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("posting_rule_versions")
public class PostingRuleVersion {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Stable identity of the rule across its versions. */
    private String ruleKey;
    /** Monotonic version within that key. */
    private int versionNumber;
    /** Book the rule posts into. */
    private UUID accountingBookId;
    /** Allowlisted kind of fact the rule consumes. */
    private String sourceFactType;
    /** Schema version of that fact the rule was written against. */
    private int sourceSchemaVersion;
    /** Market the rule is scoped to, when accounting policy differs by market. */
    private @Nullable String marketCode;
    /** Selection order when several published rules could apply. */
    private int precedence;
    /**
     * The approved rule artifact, stored whole.
     *
     * <p>Kept on the row rather than in deployed code so that "which rule ran" is answerable from the
     * record and not from whatever the application happens to say today.</p>
     */
    private JsonDocument ruleDocument;
    /** SHA-256 of that artifact, lowercase hex. */
    private String ruleDocumentHash;
    /** Version of the test vectors the rule was validated against. */
    private @Nullable String testVectorVersion;
    /** How far the version has got towards being usable. */
    private PostingRulePublicationState publicationState;
    /** UTC instant the rule starts applying to events. */
    private Instant effectiveFrom;
    /** UTC instant it stops applying. */
    private @Nullable Instant effectiveUntil;
    /** Actor who wrote it. */
    private @Nullable UUID authoredByActorId;
    /** Finance actor who approved it. */
    private @Nullable UUID approvedByActorId;
    /** UTC instant it became selectable. */
    private @Nullable Instant publishedAt;
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
