package dev.ngb.backend.admin.internal.model.export;

/**
 * Why a bulk read of production data was asked for.
 * <p>Analytics is the one purpose with an alternative: the analytical layer built in migration 030
 * exists so that routine analysis never needs a copy of production personal data.</p>
 */
public enum ExportPurposeClass {

    /** Needed to understand or resolve a live incident. */
    INCIDENT,

    /** Required by a regulator. */
    REGULATORY,

    /** Preserved because litigation requires it. */
    LEGAL_HOLD,

    /** Needed to close or reconcile the books. */
    FINANCE,

    /** Analysis, which may only ever take non-personal or pseudonymous data. */
    ANALYTICS,

    /** Moving data to another system. */
    MIGRATION,

    /** Answering a request from the person the data is about. */
    SUBJECT_REQUEST
}
