package dev.ngb.backend.model;

/**
 * Who issued a command.
 *
 * <p>Automation may pause or stop a treatment that is hurting people, because waiting for a human
 * costs more than a false pause. It may never roll one out.</p>
 */
public enum ExperimentActorKind {

    /** A person. */
    OPERATOR,

    /** A watcher acting on a breached guardrail; may only pause or stop. */
    GUARDRAIL_AUTOMATION
}
