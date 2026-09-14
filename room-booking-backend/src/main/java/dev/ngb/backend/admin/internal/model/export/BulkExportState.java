package dev.ngb.backend.admin.internal.model.export;

/**
 * Where a bulk export stands. Nothing is retrievable before approval, and nothing is retrievable
 * after the artifact expires.
 */
public enum BulkExportState {

    /** Asked for and not yet approved. */
    REQUESTED,

    /** Approved by somebody other than the requester. */
    APPROVED,

    /** The artifact exists and may be retrieved until it expires. */
    GENERATED,

    /** Refused. */
    REJECTED,

    /** Past its expiry; nothing further may be retrieved. */
    EXPIRED,

    /** Withdrawn before expiry. */
    REVOKED
}
