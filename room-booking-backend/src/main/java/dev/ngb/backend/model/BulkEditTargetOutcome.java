package dev.ngb.backend.model;

/**
 * What happened to one target of a bulk edit. Every outcome other than APPLIED carries a reason
 * code the host interface can translate.
 */
public enum BulkEditTargetOutcome {

    /** The value changed, and what it changed from and to is on the row. */
    APPLIED,

    /** The value was already what the edit asked for. */
    SKIPPED,

    /** The domain that owns the value would not change it, and said why. */
    REFUSED
}
