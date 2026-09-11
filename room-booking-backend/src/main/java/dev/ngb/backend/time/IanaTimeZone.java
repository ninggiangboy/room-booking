package dev.ngb.backend.time;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;

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

    /** Classpath resource containing the canonical IANA zone identifiers. */
    private static final String CANONICAL_ZONE_IDS_RESOURCE = "/time/canonical-zone-ids.txt";

    /** Canonical TZDB identifiers suitable for persisting as property civil zones. */
    private static final Set<String> CANONICAL_CIVIL_ZONE_IDS = loadCanonicalCivilZoneIds();

    /**
     * Snapshot of the runtime's known zone identifiers, taken once at class initialization.
     *
     * <p>{@link ZoneId#getAvailableZoneIds()} is not cached by the JDK: every call copies the
     * provider's full identifier set into a new {@code HashSet}. This sits on the Bean Validation
     * hot path through {@link #isValid(String)}, so the identifiers are copied once here instead.
     * The zone database only changes with a JDK/tzdata update, which restarts the process anyway.</p>
     */
    private static final Set<String> AVAILABLE_ZONE_IDS = Set.copyOf(ZoneId.getAvailableZoneIds());

    private IanaTimeZone() {
    }

    /**
     * Reports whether the text is a known IANA zone identifier.
     *
     * @param zoneId candidate identifier such as {@code Asia/Ho_Chi_Minh}
     * @return {@code true} when the identifier is present in the runtime's zone database
     */
    public static boolean isValid(@Nullable String zoneId) {
        return zoneId != null && AVAILABLE_ZONE_IDS.contains(zoneId);
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
        // isValid already confirmed zoneId is a catalog member (never a bare offset such as
        // "+07:00", which ZoneId.of would otherwise accept), so this cannot throw.
        return ZoneId.of(zoneId);
    }

    /**
     * Returns the zone for a property or listing, rejecting aliases and offset-only identifiers.
     *
     * @param zoneId candidate identifier
     * @param name field or configuration name used in the failure message
     * @return the resolved civil zone
     * @throws IllegalArgumentException when the identifier is unknown, an alias, or a fixed offset
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
     * <p>The TZDB exposes backward-compatible aliases alongside canonical identifiers, and a slash
     * is not enough to distinguish them: both {@code America/New_York} and {@code US/Eastern} have
     * one. The application therefore accepts only identifiers from IANA {@code zone.tab}, packaged
     * as {@value #CANONICAL_ZONE_IDS_RESOURCE}. This rejects fixed offsets, abbreviations, and all
     * compatibility aliases without relying on the runtime's zone-rule implementation.</p>
     *
     * @param zoneId identifier already known to the zone database
     * @return {@code true} when the identifier is a civil {@code Region/City} zone
     */
    private static boolean isCivilRegion(@Nullable String zoneId) {
        return zoneId != null && CANONICAL_CIVIL_ZONE_IDS.contains(zoneId);
    }

    /**
     * Loads the canonical identifiers packaged from IANA {@code zone.tab}.
     *
     * @return immutable canonical identifier set
     * @throws ExceptionInInitializerError when the required resource cannot be read
     */
    private static Set<String> loadCanonicalCivilZoneIds() {
        InputStream resource = IanaTimeZone.class.getResourceAsStream(CANONICAL_ZONE_IDS_RESOURCE);
        if (resource == null) {
            throw new ExceptionInInitializerError(
                    "Missing canonical IANA zone list: " + CANONICAL_ZONE_IDS_RESOURCE);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
