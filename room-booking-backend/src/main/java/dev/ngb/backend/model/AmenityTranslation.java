package dev.ngb.backend.model;

import java.time.Instant;

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
 * What one amenity term is called in one language.
 *
 * <p>Kept apart from the term itself so a label can be corrected without touching the key that search
 * filters and stored claims refer to.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("amenity_translations")
public class AmenityTranslation {

    /** Composite term-and-locale primary key. */
    @Id
    private AmenityTranslationId id;
    /** Label shown to a reader in this language. */
    private String label;
    /** Longer explanation shown where space allows. */
    private @Nullable String description;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
