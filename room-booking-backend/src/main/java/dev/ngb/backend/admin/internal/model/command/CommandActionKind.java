package dev.ngb.backend.admin.internal.model.command;

/**
 * What an operational command does to its target.
 * <p>The command catalogue describes what may be asked for; the domain that owns the target still
 * performs it under its own invariants.</p>
 */
public enum CommandActionKind {

    /** Stops something without ending it. */
    PAUSE,

    /** Starts a paused thing again. */
    RESUME,

    /** Ends something. */
    CANCEL,

    /** Attempts something again. */
    RETRY,

    /** Returns money. */
    REFUND,

    /** Prevents money or inventory from moving on. */
    HOLD,

    /** Lets held money or inventory move on. */
    RELEASE,

    /** Corrects an amount. */
    ADJUST,

    /** Runs something through its pipeline again. */
    REPROCESS,

    /** Finishes a case or record. */
    CLOSE,

    /** Moves work or inventory to somebody or something else. */
    REASSIGN
}
