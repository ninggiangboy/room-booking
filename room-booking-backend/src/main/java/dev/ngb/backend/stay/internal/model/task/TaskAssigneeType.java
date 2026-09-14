package dev.ngb.backend.stay.internal.model.task;

/**
 * Who is answerable for a task.
 */
public enum TaskAssigneeType {
    /** Nobody yet; work may not start. */
    UNASSIGNED,
    /** A platform account holder. */
    ACCOUNT_HOLDER,
    /** A listing collaborator acting under granted permissions. */
    COLLABORATOR,
    /** An external vendor identified by reference. */
    VENDOR
}
