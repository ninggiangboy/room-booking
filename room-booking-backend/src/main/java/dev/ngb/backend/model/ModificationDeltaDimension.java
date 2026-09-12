package dev.ngb.backend.model;

/**
 * Which aspect of a booking one delta row describes.
 *
 * <p>A guest asking why a change costs more gets an answer from these rows rather than from a total
 * that moved.</p>
 */
public enum ModificationDeltaDimension {
    /** One stay date added or removed. */
    NIGHT,
    /** A priced line added, removed or re-rated. */
    LINE_ITEM,
    /** A physical unit or pooled quantity. */
    UNIT,
    /** Party size, which may change occupancy pricing. */
    GUEST_COUNT,
    /** The rate plan and its terms. */
    RATE_PLAN,
    /** The supply being sold. */
    LISTING,
    /** A tax consequence of the change. */
    TAX,
    /** A promotion gained or lost. */
    PROMOTION,
    /** A fee gained, lost or re-rated. */
    FEE
}
