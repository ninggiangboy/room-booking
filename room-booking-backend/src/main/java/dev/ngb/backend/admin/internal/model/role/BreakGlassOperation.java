package dev.ngb.backend.admin.internal.model.role;

/**
 * What an action taken under emergency access did.
 */
public enum BreakGlassOperation {

    /** Looked at something. */
    READ,

    /** Changed something directly. */
    WRITE,

    /** Issued an operational command. */
    COMMAND,

    /** Took a copy of something out. */
    EXPORT
}
