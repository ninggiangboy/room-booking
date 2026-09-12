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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A physical, operational location where guests stay.
 *
 * <p>The property is the root of supply: an accommodation type is a sellable category *at* a
 * property, and a listing presents that category publicly. Separating them is what lets a hotel sell
 * forty identical rooms from one location while a villa sells itself once.</p>
 *
 * <p>The property owns its own IANA time zone rather than inheriting the market's, because a market
 * can span zones and every stay date, check-in time, and deadline for this property resolves in
 * <em>its</em> zone. Aliases such as {@code UTC} are refused: they carry no DST history, and a civil
 * date without DST history is not a date anyone can act on.</p>
 *
 * <p>Two positions are kept. The true coordinates drive the generated PostGIS {@code location}
 * column used for spatial search. {@link #publicLatitude} and {@link #publicLongitude} are the
 * obfuscated point shown before booking, stored rather than computed per request so every viewer
 * sees the same circle — recomputing it per request leaks the true point by triangulation.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("properties")
public class Property {

    /** Primary key of the property. */
    @Id
    private @Nullable UUID id;
    /** Account holder that owns the property and is settled for its bookings. */
    private UUID accountHolderId;
    /** Market whose rules govern selling here. */
    private String marketCode;
    /** Short human-readable reference used in operations and support. */
    private String referenceCode;
    /** Operator-facing name of the property. */
    private String displayName;
    /** What kind of physical place this is. */
    private PropertyType propertyType;
    /** Full IANA {@code Region/City} zone in which this property's civil times resolve. */
    private String timeZone;
    /** ISO 3166-1 alpha-2 country the property sits in. */
    private String countryCode;
    /** Address rendered for display, once resolved. */
    private @Nullable String formattedAddress;
    /** Reference to the protected full address record. */
    private @Nullable String addressReference;
    /** True latitude, disclosed only after booking. */
    private @Nullable BigDecimal latitude;
    /** True longitude, disclosed only after booking. */
    private @Nullable BigDecimal longitude;
    /** Obfuscated latitude shown publicly; fixed so every viewer sees the same circle. */
    private @Nullable BigDecimal publicLatitude;
    /** Obfuscated longitude shown publicly. */
    private @Nullable BigDecimal publicLongitude;
    /** How reliable the resolved coordinates are. */
    private @Nullable GeocodeConfidence geocodeConfidence;
    /** Which geocoder or process produced them. */
    private @Nullable String geocodeSource;
    /** Whether the property is in service. */
    private SupplyLifecycle lifecycleState;
    /** UTC instant the row was created, maintained by Spring Data JDBC auditing. */
    @CreatedDate
    private Instant createdAt;
    /** UTC instant the row last changed, maintained by Spring Data JDBC auditing. */
    @LastModifiedDate
    private Instant updatedAt;
    /** Optimistic-lock value checked and incremented by Spring Data. */
    @Version
    private @Nullable Long version;

    /**
     * Reports whether the property has a position precise enough to present as a location.
     *
     * <p>A low-confidence geocode is deliberately excluded: a guest choosing a property by its
     * distance to a beach is relying on the point being real, and a locality-level guess is not.</p>
     *
     * @return {@code true} when coordinates exist and were resolved precisely or pinned by the host
     */
    public boolean hasReliablePosition() {
        return latitude != null
                && longitude != null
                && geocodeConfidence != null
                && geocodeConfidence != GeocodeConfidence.LOW;
    }
}
