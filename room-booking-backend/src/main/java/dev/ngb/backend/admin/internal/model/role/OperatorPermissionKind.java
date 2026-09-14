package dev.ngb.backend.admin.internal.model.role;

/**
 * What a permission lets an operator do.
 * <p>Reading, changing state, issuing a command and exporting are separated because the rules about
 * who may be granted each, and what has to happen at the point of use, are different for each.</p>
 */
public enum OperatorPermissionKind {

    /** Returns data and changes nothing. */
    READ,

    /** Changes state directly. */
    WRITE,

    /** Asks a domain to perform a named operational command. */
    COMMAND,

    /** Reads in bulk, which is its own risk and is called out separately. */
    EXPORT,

    /** Changes who may do the other four. */
    ADMINISTER
}
