package dev.ngb.backend.model;

/**
 * What somebody did with a review in public.
 */
public enum ReviewInteractionType {
    /** It was shown to them. */
    DISPLAY,
    /** They opened the full text. */
    EXPAND,
    /** They marked it helpful. */
    HELPFUL,
    /** They marked it unhelpful. */
    UNHELPFUL,
    /** They reported it. */
    REPORT,
    /** They asked for a translation. */
    TRANSLATE
}
