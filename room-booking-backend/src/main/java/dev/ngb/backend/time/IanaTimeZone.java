package dev.ngb.backend.time;

import java.time.ZoneId;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Validation for the Internet Assigned Numbers Authority (IANA) zone identifiers the platform stores.
 *
 * <p>The private constructor prevents instantiation because this class holds no object state.
 * Listings persist a zone as text, so the application is the only place that can keep that text
 * meaningful: an offset such as {@code +07:00} or an abbreviation such as {@code ICT} does not
 * identify a civil time zone and cannot survive a future rule change.</p>
 */
public final class IanaTimeZone {

    /**
     * Identifier groups that encode a fixed offset instead of a civil region.
     *
     * <p>{@code Etc/GMT+7} and the legacy {@code SystemV} names resolve successfully but carry no
     * daylight-saving rules, so a property stored that way would silently stop tracking local time
     * if its region ever adopted one.</p>
     */
    private static final Set<String> OFFSET_ONLY_PREFIXES = Set.of("Etc/", "SystemV/");

    /** Separator that distinguishes a {@code Region/City} identifier from a legacy alias. */
    private static final char REGION_SEPARATOR = '/';

    private IanaTimeZone() {
    }

    /**
     * Reports whether the text is a known IANA zone identifier.
     *
     * @param zoneId candidate identifier such as {@code Asia/Ho_Chi_Minh}
     * @return {@code true} when the identifier is present in the runtime's zone database
     */
    public static boolean isValid(@Nullable String zoneId) {
        return zoneId != null && ZoneId.getAvailableZoneIds().contains(zoneId);
    }

    /**
     * Reports whether the text identifies a civil region suitable for a property or listing.
     *
     * @param zoneId candidate identifier such as {@code Asia/Ho_Chi_Minh}
     * @return {@code true} when the identifier names a known {@code Region/City} civil zone
     */
    public static boolean isValidCivilZone(@Nullable String zoneId) {
        return isValid(zoneId) && isCivilRegion(zoneId);
    }

    /**
     * Returns the zone for a known IANA identifier.
     *
     * @param zoneId candidate identifier
     * @param name field or configuration name used in the failure message
     * @return the resolved zone
     * @throws IllegalArgumentException when the identifier is absent or not a known IANA zone
     */
    public static ZoneId parse(@Nullable String zoneId, String name) {
        if (!isValid(zoneId)) {
            throw new IllegalArgumentException(
                    name + " must be a known IANA time-zone identifier such as 'Asia/Ho_Chi_Minh'"
                            + " but was '" + zoneId + "'");
        }
        return ZoneId.of(zoneId);
    }

    /**
     * Returns the zone for a property or listing, rejecting offset-only identifiers.
     *
     * @param zoneId candidate identifier
     * @param name field or configuration name used in the failure message
     * @return the resolved civil zone
     * @throws IllegalArgumentException when the identifier is unknown or encodes a fixed offset
     */
    public static ZoneId parseCivilZone(@Nullable String zoneId, String name) {
        ZoneId zone = parse(zoneId, name);
        if (!isCivilRegion(zoneId)) {
            throw new IllegalArgumentException(
                    name + " must identify a civil region such as 'Asia/Ho_Chi_Minh' rather than a"
                            + " fixed offset or legacy alias but was '" + zoneId + "'");
        }
        return zone;
    }

    /**
     * Reports whether a known identifier names a civil region rather than an offset or alias.
     *
     * <p>The check is structural rather than a list of known-bad names. A property zone must be a
     * {@code Region/City} identifier, which rejects every single-segment alias the zone database
     * still carries for compatibility: fixed offsets such as {@code UTC}, {@code GMT},
     * {@code Zulu}, and {@code Universal} identify no region at all, and aggregates such as
     * {@code CET} or {@code Japan} carry rules that are not owned by any jurisdiction the platform
     * can track. The two offset-only groups that do contain a separator are excluded explicitly.</p>
     *
     * <p>{@link java.time.zone.ZoneRules#isFixedOffset()} is deliberately not used for this test.
     * It reports {@code true} for a region whose historical transitions are absent, so a runtime
     * shipping a reduced zone database could start rejecting legitimate properties.</p>
     *
     * @param zoneId identifier already known to the zone database
     * @return {@code true} when the identifier is a civil {@code Region/City} zone
     */
    private static boolean isCivilRegion(@Nullable String zoneId) {
        if (zoneId == null || zoneId.indexOf(REGION_SEPARATOR) < 0) {
            return false;
        }
        return OFFSET_ONLY_PREFIXES.stream().noneMatch(zoneId::startsWith);
    }
}
