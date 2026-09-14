package dev.ngb.backend.admin.internal.model.export;

/**
 * Where an export artifact goes.
 */
public enum ExportDestinationKind {

    /** Platform-controlled storage with its own access controls. */
    SECURE_BUCKET,

    /** A monitored workspace an analyst reads it in. */
    ANALYST_WORKSPACE,

    /** A transfer channel agreed with a regulator. */
    REGULATOR_TRANSFER,

    /** Delivery to counsel under privilege. */
    LEGAL_COUNSEL,

    /** Delivery to the person the data is about. */
    SUBJECT_DELIVERY
}
