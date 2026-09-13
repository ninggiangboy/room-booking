package dev.ngb.backend.model;

/**
 * Where a demand forecast run stands. Forecasts are written while it is running and the run is then
 * closed against its own row count, so a truncated run cannot report a complete one.
 */
public enum DemandForecastRunState {

    /** Registered but not started. */
    PENDING,

    /** Producing forecasts; the only state in which forecasts may be written. */
    RUNNING,

    /** Finished, with its row count checked against the forecasts it holds. */
    SUCCEEDED,

    /** Stopped without producing a usable set, and the reason is on the row. */
    FAILED,

    /** Replaced by a later run, which is what every fresh run does to the one before it. */
    SUPERSEDED
}
