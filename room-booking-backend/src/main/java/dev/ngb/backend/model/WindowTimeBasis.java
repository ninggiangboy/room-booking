package dev.ngb.backend.model;

/**
 * Which clock a metric window is measured against.
 *
 * <p>A late but valid fact belongs in the event-time window it happened in, even though it arrived
 * after that window closed.</p>
 */
public enum WindowTimeBasis {

    /** When the fact happened. */
    EVENT_TIME,

    /** When the platform persisted it. */
    INGEST_TIME
}
