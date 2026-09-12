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
import org.springframework.data.relational.core.mapping.Table;

/**
 * A derived reading of an exact revision, never a replacement for it.
 *
 * <p>Keyed by the source digest as well as the revision, so a translation cannot silently survive a
 * correction to the text it renders.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("review_translations")
public class ReviewTranslation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Revision translated. */
    private UUID reviewRevisionId;
    /** Digest of the text that was translated. */
    private String sourceDigest;
    /** Locale translated into. */
    private String targetLocale;
    /** Who or what produced it. */
    private TranslationSource translationSource;
    /** Engine version, for a machine translation. */
    private @Nullable String engineVersion;
    /** Glossary applied. */
    private @Nullable String glossaryVersion;
    /** The translation, when stored here. */
    private @Nullable String translatedText;
    /** Where it lives, when stored elsewhere. */
    private @Nullable String contentReference;
    /** How sure the engine was. */
    private @Nullable BigDecimal confidence;
    /** What moderation says about the translation. */
    private ReviewModerationState moderationState;
    /** Translation that replaced this one. */
    private @Nullable UUID supersededByTranslationId;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
