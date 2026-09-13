package dev.ngb.backend.model;

/**
 * What a validation concluded. Anything other than a pass has to say what it found.
 */
public enum ChangeValidationOutcome {

    /** The check found nothing. */
    PASS,

    /** The check found something worth reading before approving. */
    WARN,

    /** The check found something that stops the change. */
    FAIL,

    /** The check did not run, and the row says why. */
    SKIPPED
}
