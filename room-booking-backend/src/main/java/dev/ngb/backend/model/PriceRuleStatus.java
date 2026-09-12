package dev.ngb.backend.model;

/**
 * Whether a price rule's identity is in use.
 *
 * <p>Distinct from the publication state of its versions: a rule can be {@code ACTIVE} while its
 * latest version is still a draft, and retiring a rule never alters versions that priced past stays.</p>
 */
public enum PriceRuleStatus {
    /** Being written; no version of it may price anything. */
    DRAFT,
    /** In use, subject to a published version being in force. */
    ACTIVE,
    /** Temporarily not applied, without being given up. */
    PAUSED,
    /** Permanently withdrawn from future pricing. */
    RETIRED
}
