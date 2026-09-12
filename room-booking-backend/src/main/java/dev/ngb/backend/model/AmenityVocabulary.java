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
 * One version of the controlled amenity vocabulary.
 *
 * <p>Versioning matters because search filters, guest expectations, and historical bookings all cite
 * amenity terms. Renaming "Wifi" to "Wi-Fi" must not silently change what a guest booked last year,
 * and retiring a term must not orphan the listings that claimed it — so versions are superseded and
 * retained rather than edited.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("amenity_vocabularies")
public class AmenityVocabulary {

    /** Primary key of the vocabulary version. */
    @Id
    private @Nullable UUID id;
    /** Stable key of the vocabulary. */
    private String vocabularyKey;
    /** Monotonic version number within that key. */
    private int vocabularyVersion;
    /** Whether the version may be used for new and edited supply. */
    private VocabularyStatus status;
    /** UTC instant the version takes effect, inclusive. */
    private Instant effectiveFrom;
    /** UTC instant it stops applying, exclusive; {@code null} while current. */
    private @Nullable Instant effectiveUntil;
    /** UTC instant the row was written. */
    private Instant createdAt;
}
