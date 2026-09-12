package dev.ngb.backend.model;

/**
 * Where a versioned, immutable payload stands in its life.
 *
 * <p>Shared by price rule versions, promotion versions, and tax rule versions, because all three
 * answer the same question: may this be cited by a priced row, and may it still be edited?</p>
 *
 * <p>The transition {@code DRAFT → PUBLISHED} is one-way. Once published, the payload is evidence
 * that a guest was charged under specific terms; {@code RETIRED} ends its future use without
 * rewriting what it said, and returning to {@code DRAFT} is refused by the database.</p>
 */
public enum PublicationState {
    /** Editable, and cited by nothing. */
    DRAFT,
    /** Frozen and citable; the payload can no longer change. */
    PUBLISHED,
    /** No longer applied to new decisions, but preserved exactly as published. */
    RETIRED
}
