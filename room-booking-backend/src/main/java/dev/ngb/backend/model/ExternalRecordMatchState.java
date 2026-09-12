package dev.ngb.backend.model;

/**
 * Whether an external row has been accounted for.
 *
 * <p>This is the only field of an external record that may change after ingestion. Everything else is
 * frozen, because a row whose amount can be edited proves nothing.</p>
 */
public enum ExternalRecordMatchState {
    /** Not yet compared, or compared without a match. */
    UNMATCHED,
    /** Tied to an internal fact by an approved rule. */
    MATCHED,
    /** Compared and disagreed; a case owns it. */
    EXCEPTION,
    /** Outside the coverage of any control that applies. */
    OUT_OF_SCOPE
}
