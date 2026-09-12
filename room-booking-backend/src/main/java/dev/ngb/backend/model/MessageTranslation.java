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
 * A machine or human translation of one message, kept beside the original.
 *
 * <p>Append-only by trigger, and keyed by engine version: re-translating with a better model adds a
 * row rather than changing what a reader was shown yesterday. A translation is never evidence that
 * the author made a statement in the target language.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("message_translations")
public class MessageTranslation {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Message being translated. */
    private UUID messageId;
    /** Which text was translated; zero means the original message. */
    private short sourceRevisionNumber;
    /** Locale translated into. */
    private String targetLocale;
    /** Whether a person, a machine or a professional produced it. */
    private TranslationSource translationSource;
    /** Translation engine, required for machine translation. */
    private @Nullable String engineKey;
    /** Version of that engine, part of the identity of this translation. */
    private String engineVersion;
    /** Glossary applied, which protects placeholders and policy terms. */
    private @Nullable String glossaryVersion;
    /** Translation, when stored inline. */
    private @Nullable String translatedText;
    /** Pointer to the translation in storage. */
    private @Nullable String translatedReference;
    /** Engine confidence between zero and one, when reported. */
    private @Nullable BigDecimal confidence;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
