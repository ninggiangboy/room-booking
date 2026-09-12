package dev.ngb.backend.model;

import java.time.Instant;
import java.time.LocalDate;
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
 * Whether a property has a given piece of safety equipment.
 *
 * <p>Absence is recorded explicitly as {@code false} rather than as a missing row. "This property has
 * no smoke alarm" is information a guest is entitled to before booking; "nobody asked" is a different
 * statement, and one row shape cannot carry both.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("property_safety_items")
public class PropertySafetyItem {

    /** Primary key of the declaration. */
    @Id
    private @Nullable UUID id;
    /** Property the declaration is about. */
    private UUID propertyId;
    /** Stable key of the safety item, such as {@code SMOKE_ALARM}. */
    private String safetyItemKey;
    /** Whether the item is present; {@code false} is a real answer, not a missing one. */
    private boolean isPresent;
    /** Civil date the item was last checked, where the host records it. */
    private @Nullable LocalDate lastCheckedOn;
    /** Anything the host adds about the item. */
    private @Nullable String notes;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;
}
