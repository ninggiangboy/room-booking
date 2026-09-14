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
import dev.ngb.backend.platform.ConsentLegalBasis;
import dev.ngb.backend.platform.RetentionClass;

import dev.ngb.backend.platform.ConsentLegalBasis;
import dev.ngb.backend.platform.RetentionClass;


/**
 * What one party said privately, to the other or to the platform.
 *
 * <p>Never meant to be public, and explicitly excluded from aggregates and ordinary aspect
 * processing: it is not a rating and must not become one by being counted. The exclusion is a stored
 * flag rather than an implicit rule, so a later pipeline that wants this material has to change a
 * column and be seen doing it.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_private_feedback")
public class ReviewPrivateFeedback {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Revision it was submitted alongside. */
    private @Nullable UUID reviewRevisionId;
    /** Right it was written under. */
    private UUID reviewRightId;
    /** Who it was written for. */
    private PrivateFeedbackAudience audience;
    /** Why it may be read. */
    private String purposeCode;
    /** Where the encrypted payload lives. */
    private String payloadReference;
    /** Hash of that payload. */
    private String payloadDigest;
    /** Key reference needed to read it. */
    private @Nullable String encryptionKeyReference;
    /** Lawful basis for holding it. */
    private ConsentLegalBasis legalBasis;
    /** Consent record, when the basis is consent. */
    private @Nullable String consentReference;
    /** How long it is kept. */
    private RetentionClass retentionClass;
    /** Whether deletion is barred. */
    private boolean legalHold;
    /** Whether ratings must ignore it. */
    private boolean excludedFromAggregates;
    /** Whether extraction must ignore it. */
    private boolean excludedFromIntelligence;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
