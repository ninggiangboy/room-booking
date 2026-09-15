package dev.ngb.backend.trust.internal.model.decision;

/**
 * What was decided about one content revision.
 *
 * <p>A decision that makes content visible must name the latest revision of its item, so an old
 * approval can never publish a new revision. Removals and safety escalations may always name an older
 * one, because they are about what was said then.</p>
 */
public enum ModerationOutcome {
    /** The exact revision is visible to its audience. */
    PUBLISH,
    /** Approved spans are replaced; the original is kept as protected evidence. */
    MASK,
    /** It may proceed behind a contextual warning. */
    WARN,
    /** Hidden pending a time-bounded review. */
    QUARANTINE,
    /** Not published or sent. */
    REJECT,
    /** A previously visible revision is hidden. */
    REMOVE,
    /** Routed to the urgent safety workflow; visibility is decided separately. */
    ESCALATE_SAFETY
}
