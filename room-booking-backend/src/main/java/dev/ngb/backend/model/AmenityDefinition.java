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
 * One amenity term within a vocabulary version.
 *
 * <p>The key is stable and language-free; what a reader sees lives in {@code amenity_translations}.
 * That split is what lets search and storage work on the key while presentation follows the reader's
 * language, instead of a Vietnamese guest and an English one filtering on different things.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("amenity_definitions")
public class AmenityDefinition {

    /** Primary key of the definition. */
    @Id
    private @Nullable UUID id;
    /** Vocabulary version this term belongs to. */
    private UUID amenityVocabularyId;
    /** Stable, language-free key such as {@code WIFI}. */
    private String amenityKey;
    /** Grouping used to organise the term for presentation. */
    private String category;
    /** What shape the term's value takes. */
    private AmenityValueType valueType;
    /** Whether guests may filter search results on this term. */
    private boolean isSearchable;
    /** Whether claiming this term requires supporting evidence. */
    private boolean requiresEvidence;
    /** Position within its category. */
    private short sortOrder;
    /** UTC instant the row was written. */
    private Instant createdAt;
}
