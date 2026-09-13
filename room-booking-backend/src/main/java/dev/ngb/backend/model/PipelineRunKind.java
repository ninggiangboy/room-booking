package dev.ngb.backend.model;

/**
 * Why a transformation ran.
 */
public enum PipelineRunKind {

    /** The ordinary recurring run. */
    SCHEDULED,

    /** Reconstructing history for a period not previously computed. */
    BACKFILL,

    /** Recomputing a period whose inputs changed. */
    RESTATEMENT,

    /** Run by hand outside the schedule. */
    MANUAL
}
