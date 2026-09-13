package dev.ngb.backend.model;

/**
 * How strongly a checklist item is put to the host. An item that blocks publication is REQUIRED and
 * not dismissible; calling it a suggestion while it stops the listing going live tells the host it
 * is advice and lets them discover it is a gate.
 */
public enum ChecklistItemSeverity {

    /** Worth knowing; nothing is expected of the host. */
    INFO,

    /** Likely to help, and the host may set it aside. */
    SUGGESTED,

    /** Materially affects how the listing performs. */
    IMPORTANT,

    /** The only severity an item that blocks publication may carry. */
    REQUIRED
}
