package dev.ngb.backend.model;

/**
 * Who owns a promotion, and therefore whose money is at stake by default.
 *
 * <p>A host promotion must name the host account it belongs to; a platform promotion must not borrow
 * a host's identity for a cost that host never agreed to bear.</p>
 */
public enum PromotionOwnerType {
    /** Run centrally and funded from platform budget unless the split says otherwise. */
    PLATFORM,
    /** Created by a host for their own supply. */
    HOST
}
