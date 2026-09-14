package dev.ngb.backend.review.internal.model.aspect;

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
 * One aspect within one taxonomy version.
 *
 * <p>The sensitivity tier lives here rather than in code, because it decides whether an aspect may be
 * shown to a guest, fed to ranking, or used at all.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("aspect_definitions")
public class AspectDefinition {

    /** Primary key. */
    @Id
    private @Nullable UUID id;
    /** Taxonomy version this belongs to. */
    private UUID aspectTaxonomyVersionId;
    /** Stable code for the aspect. */
    private String aspectCode;
    /** Group it is presented under. */
    private String aspectGroup;
    /** What it means, for the extractor and for review. */
    private String definitionText;
    /** What it may be said about. */
    private String[] allowedTargets;
    /** How freely it may be used. */
    private AspectSensitivityTier sensitiveUseTier;
    /** Where its translated labels live. */
    private @Nullable String localizedLabelReference;
    /** Where its examples live. */
    private @Nullable String exampleReference;
    /** Aspect that replaces it in a later taxonomy. */
    private @Nullable String successorAspectCode;
    /** When it stopped being used. */
    private @Nullable Instant deprecatedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
}
