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
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One attempt to resolve a property's address into coordinates, or the reverse.
 *
 * <p>Every attempt is recorded, not only the one that won. When a property's position is wrong a
 * guest is sent to the wrong place, and diagnosing that needs to know which provider produced the
 * point, how confident it claimed to be, and what the host had actually typed at the time.</p>
 *
 * <p>{@link #isApplied} marks the attempt the property's coordinates actually came from — at most one
 * per property — and the database refuses to let a failed attempt be the applied one. Superseded
 * attempts are kept rather than overwritten.</p>
 *
 * <p>There is no {@code @Version}: the row records what a provider said at a moment and is never
 * revised.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table("geocoding_results")
public class GeocodingResult {

    /** Primary key of the attempt. */
    @Id
    private @Nullable UUID id;
    /** Property whose position was being resolved. */
    private UUID propertyId;
    /** Whether the request went address-to-coordinates or the reverse. */
    private GeocodingDirection direction;
    /** Provider account used, so the attempt stays attributable after a credential rotation. */
    private @Nullable String providerAccountKey;
    /** Version of that provider account. */
    private @Nullable Short providerAccountVersion;
    /** SHA-256 digest of the query, so the same input is recognisable without storing an address. */
    private String queryDigest;
    /** Latitude the provider returned. */
    private @Nullable BigDecimal matchedLatitude;
    /** Longitude the provider returned. */
    private @Nullable BigDecimal matchedLongitude;
    /** Address the provider returned, for a reverse lookup. */
    private @Nullable String matchedAddress;
    /** How confident the provider was in the match. */
    private GeocodeConfidence confidence;
    /** Provider-specific description of what it matched on. */
    private @Nullable String matchType;
    /** Provider response retained as immutable evidence. */
    private @Nullable JsonDocument rawResponse;
    /** Whether the property's coordinates came from this attempt. */
    private boolean isApplied;
    /** UTC instant the request was made. */
    private Instant requestedAt;
    /** UTC instant the row was written. */
    private Instant createdAt;
}
