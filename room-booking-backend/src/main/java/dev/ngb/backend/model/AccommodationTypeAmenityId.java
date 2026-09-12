package dev.ngb.backend.model;

import java.io.Serializable;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Composite identifier pairing an accommodation type with an amenity term it claims.
 *
 * <p>{@code @EqualsAndHashCode} gives the key value semantics, and {@link Serializable} lets
 * persistence infrastructure transport the compound key as one value.</p>
 */
@Getter
@EqualsAndHashCode
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AccommodationTypeAmenityId implements Serializable {

    /** Accommodation type side of the compound key. */
    private UUID accommodationTypeId;
    /** Amenity term side of the compound key. */
    private UUID amenityDefinitionId;
}
