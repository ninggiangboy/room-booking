package dev.ngb.backend.model;

/**
 * How far an approved posting rule has travelled towards being usable.
 *
 * <p>A transaction pins the rule version it used. Replaying a two-year-old booking therefore
 * produces the entries that were correct two years ago, not the entries today's chart of accounts
 * would produce.</p>
 */
public enum PostingRulePublicationState {
    /** Authored and still editable. */
    DRAFT,
    /** Signed off by finance and awaiting publication. */
    APPROVED,
    /** Selectable by the posting engine within its effective interval. */
    PUBLISHED,
    /** Replaced by a later version; still used for historical replay. */
    SUPERSEDED,
    /** Pulled before it was ever used. */
    WITHDRAWN
}
