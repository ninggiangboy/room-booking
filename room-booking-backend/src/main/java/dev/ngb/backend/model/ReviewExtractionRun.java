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
 * One attempt to read aspects out of one exact revision.
 *
 * <p>Every version that could change the answer is on the row: taxonomy, extractor, model, prompt,
 * provider and config. Replaying the same revision through the same versions must produce the same
 * output, and a partial unique index allows only one successful run per that identity -- a rerun that
 * succeeded twice would double every mention it produced.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_extraction_runs")
public class ReviewExtractionRun {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Revision read. */
    private UUID reviewRevisionId;
    /** Digest of the text that was read. */
    private String sourceDigest;
    /** Vocabulary it was read against. */
    private UUID aspectTaxonomyVersionId;
    /** Extractor version. */
    private String extractorVersion;
    /** Model version, where a model was used. */
    private @Nullable String modelVersion;
    /** Prompt version, where a prompt was used. */
    private @Nullable String promptVersion;
    /** Provider account that served it. */
    private @Nullable UUID providerAccountId;
    /** Configuration version. */
    private @Nullable String configVersion;
    /** Redaction applied to the input. */
    private @Nullable String inputDisclosureVersion;
    /** How far the run has got. */
    private ExtractionRunState state;
    /** Which attempt this is. */
    private int attemptNumber;
    /** Monotonic token a late worker is fenced against. */
    private long fencingToken;
    /** When a worker claimed it. */
    private @Nullable Instant claimedAt;
    /** Which worker holds the lease. */
    private @Nullable String claimedBy;
    /** When that lease lapses. */
    private @Nullable Instant leaseExpiresAt;
    /** When work began. */
    private @Nullable Instant startedAt;
    /** When it finished. */
    private @Nullable Instant completedAt;
    /** Hash of what it produced. */
    private @Nullable String outputDigest;
    /** What validation made of that output. */
    private @Nullable String validationSummary;
    /** Why it failed. */
    private @Nullable String errorClass;
    /** What it cost. */
    private @Nullable Long costMicros;
    /** How long it took. */
    private @Nullable Integer latencyMillis;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic lock counter. */
    @Version
    private long version;

    /**
     * Whether this run produced mentions that may be counted.
     *
     * @return true once it succeeded with an output digest
     */
    public boolean producedOutput() {
        return state == ExtractionRunState.SUCCEEDED && outputDigest != null;
    }
}
