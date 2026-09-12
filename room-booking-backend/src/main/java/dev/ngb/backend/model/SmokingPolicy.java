package dev.ngb.backend.model;

/**
 * Where, if anywhere, smoking is permitted at a property.
 *
 * <p>Separate from {@link RulePolicy} because the useful middle answer is spatial rather than
 * permission-based: most properties that allow smoking allow it outdoors only.</p>
 */
public enum SmokingPolicy {
    /** Permitted anywhere at the property. */
    ALLOWED,
    /** Permitted outdoors only. */
    OUTSIDE_ONLY,
    /** Not permitted anywhere. */
    NOT_ALLOWED
}
