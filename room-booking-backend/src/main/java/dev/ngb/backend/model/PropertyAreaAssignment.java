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
 * One geographic area a property should appear under in search.
 *
 * <p>A property is simultaneously in a country, a province, a city, and a neighbourhood, so there are
 * several of these per property. {@code properties.geo_area_id} answers the different question of
 * which single destination the property is filed under.</p>
 *
 * <p>{@link #assignmentSource} is what makes catalog reimports safe: a boundary-derived assignment
 * can be recomputed, while a host-declared or operator-corrected one must survive. Without it, every
 * reimport silently discards the human corrections.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("property_area_assignments")
public class PropertyAreaAssignment {

    /** Composite property-and-area primary key. */
    @Id
    private PropertyAreaAssignmentId id;
    /** How the assignment was decided. */
    private AreaAssignmentSource assignmentSource;
    /** Whether this is the destination the property is primarily filed under. */
    private boolean isPrimary;
    /** Version of the geographic catalog the assignment was computed against. */
    private @Nullable String catalogVersion;
    /** UTC instant the assignment was made. */
    private Instant assignedAt;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;

    /**
     * Reports whether a catalog reimport may safely recompute this assignment.
     *
     * <p>Human decisions are preserved; machine-derived ones are rebuilt.</p>
     *
     * @return {@code true} when the assignment was derived rather than stated by a person
     */
    public boolean isRecomputable() {
        return assignmentSource == AreaAssignmentSource.BOUNDARY
                || assignmentSource == AreaAssignmentSource.PROXIMITY;
    }
}
