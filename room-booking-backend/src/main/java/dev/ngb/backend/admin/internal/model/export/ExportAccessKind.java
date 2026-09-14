package dev.ngb.backend.admin.internal.model.export;

/**
 * What a retrieval of an export artifact did.
 */
public enum ExportAccessKind {

    /** Took a copy. */
    DOWNLOAD,

    /** Looked at part of it without taking a copy. */
    PREVIEW,

    /** Sent it on to the approved destination. */
    TRANSFER,

    /** Checked the artifact digest without reading the contents. */
    VERIFY
}
