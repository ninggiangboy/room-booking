package dev.ngb.backend.model;

/**
 * What kind of thing an incident event points at.
 */
public enum IncidentEventReferenceType {
    /** A conversation message. */
    MESSAGE,
    /** An evidence link on this incident. */
    EVIDENCE_LINK,
    /** A remedy request on this incident. */
    REMEDY_REQUEST,
    /** A preparation task. */
    OPERATIONAL_TASK,
    /** A maintenance record. */
    MAINTENANCE_RECORD,
    /** An access grant. */
    ACCESS_GRANT
}
