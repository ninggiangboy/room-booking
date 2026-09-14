package dev.ngb.backend.supply.internal.model.geo;

import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;


/**
 * Composite identifier pairing a destination with one of its names in one language.
 *
 * <p>The normalized form is part of the key rather than a derived attribute because a destination
 * may hold several names in the same language -- an official one, an abbreviation, a colloquial
 * one -- and they are told apart by what they normalize to.</p>
 *
 * <p>{@code @EqualsAndHashCode} gives the key value semantics, and {@link Serializable} lets
 * persistence infrastructure transport the compound key as one value.</p>
 */
@Getter
@EqualsAndHashCode
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class GeoAreaNameId implements Serializable {

    /** Destination side of the compound key. */
    private UUID geoAreaId;
    /** BCP 47 language side of the compound key; {@code und} where the source states no language. */
    private String languageCode;
    /** Normalized name that distinguishes this name from the destination's others. */
    private String normalizedName;
}
