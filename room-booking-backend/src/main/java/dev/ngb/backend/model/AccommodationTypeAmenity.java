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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

/**
 * An amenity an accommodation type claims to have.
 *
 * <p>The claim points at a term in a specific vocabulary version, so what the host claimed stays
 * interpretable after the vocabulary moves on. Terms whose value type carries a quantity or a string
 * store it here rather than needing a separate boolean key per value.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("accommodation_type_amenities")
public class AccommodationTypeAmenity {

    /** Composite accommodation-type-and-term primary key. */
    @Id
    private AccommodationTypeAmenityId id;
    /** Quantity, for a term whose value type is a count. */
    private @Nullable Short countValue;
    /** Free text, for a term whose value type is text or an enumeration. */
    private @Nullable String textValue;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
}
