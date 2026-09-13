package dev.ngb.backend.model;

/**
 * Where a listing stands on one checklist item. SATISFIED means the platform looked again and the
 * thing it complained about is gone, which is a different claim from the host having pressed a
 * button.
 */
public enum ChecklistItemState {

    /** Outstanding, and shown to the host as something to act on. */
    OPEN,

    /** The host acted and the platform looked again and agreed. */
    SATISFIED,

    /** The host set it aside, which they may do only for items that allow it. */
    DISMISSED,

    /** It does not apply to this listing, and the reason is recorded. */
    NOT_APPLICABLE,

    /** It stopped being relevant before it was resolved. */
    EXPIRED
}
