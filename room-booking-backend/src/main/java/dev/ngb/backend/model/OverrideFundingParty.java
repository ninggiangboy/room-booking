package dev.ngb.backend.model;

/**
 * Who pays for an override.
 *
 * <p>{@code SHARED} is not vagueness: the programme version carries an exhaustive basis-point split,
 * and the row refuses a split that does not total the whole.</p>
 */
public enum OverrideFundingParty {
    /** The guest absorbs it. */
    GUEST,
    /** The host absorbs it. */
    HOST,
    /** The platform absorbs it. */
    PLATFORM,
    /** Split between parties by the recorded basis points. */
    SHARED
}
